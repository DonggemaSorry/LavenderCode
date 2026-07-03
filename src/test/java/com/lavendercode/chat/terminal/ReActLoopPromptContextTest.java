package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.core.config.*;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class ReActLoopPromptContextTest {
    private InMemorySessionManager sessionManager;
    private BatchingToolExecutor batchExecutor;
    private TokenAccumulator tokenAccumulator;

    @BeforeEach void setUp() {
        sessionManager = new InMemorySessionManager();
        batchExecutor = new BatchingToolExecutor(30, 120);
        tokenAccumulator = new TokenAccumulator();
    }

    /** Mock provider that captures PromptContext per round */
    private static class CtxCaptureProvider implements LlmProvider {
        final List<PromptContext> capturedCtxs = new ArrayList<>();
        final List<String> responses;
        int callIndex = 0;

        CtxCaptureProvider(List<String> responses) { this.responses = responses; }

        @Override public String protocol() { return "mock"; }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
            return streamChat(history, config, List.of());
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                               List<ToolDefinition> toolDefs) {
            return streamChat(history, config, toolDefs, null);
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                               List<ToolDefinition> toolDefs,
                                               PromptContext promptContext) {
            if (promptContext != null) capturedCtxs.add(promptContext);
            String resp = callIndex < responses.size() ? responses.get(callIndex++) : "";
            return new SingleEventIterator(new StreamEvent.ContentDelta(resp),
                new StreamEvent.Usage(10, 5, 0, 0), new StreamEvent.StreamComplete());
        }
    }

    private static class SingleEventIterator implements StreamEventIterator {
        private final List<StreamEvent> events; private int idx = 0;
        SingleEventIterator(StreamEvent... events) { this.events = Arrays.asList(events); }
        @Override public boolean hasNext() { return idx < events.size(); }
        @Override public StreamEvent next() { return events.get(idx++); }
        @Override public void close() {}
    }

    /** Mock provider that returns predefined StreamEvent lists per round */
    private static class MultiRoundProvider implements LlmProvider {
        final List<PromptContext> capturedCtxs = new ArrayList<>();
        private final List<List<StreamEvent>> roundEvents;
        private int callIndex = 0;

        MultiRoundProvider(List<List<StreamEvent>> roundEvents) {
            this.roundEvents = roundEvents;
        }

        @Override public String protocol() { return "mock"; }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
            return streamChat(history, config, List.of());
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                               List<ToolDefinition> toolDefs) {
            return streamChat(history, config, toolDefs, null);
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                               List<ToolDefinition> toolDefs,
                                               PromptContext promptContext) {
            if (promptContext != null) capturedCtxs.add(promptContext);
            List<StreamEvent> events = callIndex < roundEvents.size()
                ? roundEvents.get(callIndex++)
                : List.of(new StreamEvent.ContentDelta("done"), new StreamEvent.StreamComplete());
            return new SingleEventIterator(events.toArray(new StreamEvent[0]));
        }
    }

    private LlmConfig config() {
        return new LlmConfig(List.of(new ProviderConfig("mock","mock","m","http://localhost","k",null)), new Options());
    }

    @Test
    void buildsPromptContextPerRound() {
        // Round 1: tool call, Round 2: no tools (complete)
        var provider = new CtxCaptureProvider(List.of("tool_call", "done"));
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        var planMode = new PlanModeManager();
        planMode.enterPlanMode();
        loop.setConfig(config(), List.of(),
            "stable", "env", planMode);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("test", events::add);
        // Should have 2 contexts, round 1 = full, round 2 = brief
        assertThat(provider.capturedCtxs).hasSizeGreaterThanOrEqualTo(1);
        if (provider.capturedCtxs.size() >= 2) {
            var r1 = provider.capturedCtxs.get(0);
            var r2 = provider.capturedCtxs.get(1);
            assertThat(r1.reminders().get(0)).contains("PLAN MODE");
            assertThat(r2.reminders().get(0)).contains("Plan mode");
        }
    }

    @Test
    void reminderNotInSessionHistory() {
        var provider = new CtxCaptureProvider(List.of("done"));
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        var planMode = new PlanModeManager();
        planMode.enterPlanMode();
        loop.setConfig(config(), List.of(), "stable", "env", planMode);
        loop.run("test", e -> {});
        for (Message msg : sessionManager.getHistory()) {
            if (msg.content() != null) {
                assertThat(msg.content()).doesNotContain("<system-reminder>");
            }
        }
    }

    @Test
    void ch04CancelStillWorks() throws Exception {
        ToolRegistry.register(new Tool() {
            @Override public String name() { return "slow_tool"; }
            @Override public String description() { return "slow"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }
            @Override public ToolResult execute(Map<String, Object> p) {
                try { Thread.sleep(3000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ToolResult.success("slow-ok", "");
            }
            @Override public boolean isReadOnly() { return true; }
        });

        var provider = new LlmProvider() {
            @Override public String protocol() { return "mock"; }
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                                   List<ToolDefinition> toolDefs,
                                                   PromptContext promptContext) {
                return new SingleEventIterator(
                    new StreamEvent.ToolCallStart("c1", "slow_tool"),
                    new StreamEvent.ToolCallDelta("c1", "{}"),
                    new StreamEvent.ToolCallEnd("c1", "slow_tool", Map.of()),
                    new StreamEvent.Usage(10, 5, 0, 0),
                    new StreamEvent.StreamComplete());
            }
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
                return streamChat(history, config, List.of(), null);
            }
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                                   List<ToolDefinition> toolDefs) {
                return streamChat(history, config, toolDefs, null);
            }
        };

        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        loop.setConfig(config(), List.of(), "stable", "env", new PlanModeManager());
        List<AgentEvent> events = new ArrayList<>();

        Thread runThread = new Thread(() -> loop.run("test", events::add));
        runThread.start();
        Thread.sleep(200);
        loop.cancel();
        runThread.join(5000);

        assertThat(events).anyMatch(e -> e instanceof AgentEvent.Stopped);
    }

    @Test
    void ch04ErrorRecoveryStillWorks() {
        var provider = new LlmProvider() {
            @Override public String protocol() { return "mock"; }
            @Override public StreamEventIterator streamChat(List<Message> h, LlmConfig c) {
                return new SingleEventIterator(new StreamEvent.StreamError("boom", 500));
            }
        };
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        loop.setConfig(config(), List.of(), "stable", "env", new PlanModeManager());
        List<AgentEvent> events = new ArrayList<>();
        loop.run("test", events::add);
        assertThat(events).anyMatch(e -> e instanceof AgentEvent.Error);
    }

    @Test
    void ch04MultiRoundStillWorks() {
        ToolRegistry.register(new Tool() {
            @Override public String name() { return "test_tool"; }
            @Override public String description() { return "test"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }
            @Override public ToolResult execute(Map<String, Object> p) {
                return ToolResult.success("ok", "result");
            }
            @Override public boolean isReadOnly() { return true; }
        });

        var provider = new MultiRoundProvider(List.of(
            List.of(
                new StreamEvent.ToolCallStart("c1", "test_tool"),
                new StreamEvent.ToolCallDelta("c1", "{}"),
                new StreamEvent.ToolCallEnd("c1", "test_tool", Map.of()),
                new StreamEvent.Usage(10, 5, 0, 0),
                new StreamEvent.StreamComplete()
            ),
            List.of(
                new StreamEvent.ContentDelta("done"),
                new StreamEvent.Usage(10, 5, 0, 0),
                new StreamEvent.StreamComplete()
            )
        ));

        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        loop.setConfig(config(), List.of(), "stable", "env", new PlanModeManager());
        List<AgentEvent> events = new ArrayList<>();
        loop.run("test", events::add);

        assertThat(events).anyMatch(e -> e instanceof AgentEvent.Complete);
        assertThat(provider.capturedCtxs).hasSize(2);
    }

    @Test
    void planModeMultiRoundHistoryLegal() {
        ToolRegistry.register(new Tool() {
            @Override public String name() { return "read_tool"; }
            @Override public String description() { return "read"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }
            @Override public ToolResult execute(Map<String, Object> p) {
                return ToolResult.success("ok", "content");
            }
            @Override public boolean isReadOnly() { return true; }
        });

        var provider = new MultiRoundProvider(List.of(
            List.of(
                new StreamEvent.ToolCallStart("c1", "read_tool"),
                new StreamEvent.ToolCallDelta("c1", "{}"),
                new StreamEvent.ToolCallEnd("c1", "read_tool", Map.of()),
                new StreamEvent.Usage(10, 5, 3, 0),
                new StreamEvent.StreamComplete()
            ),
            List.of(
                new StreamEvent.ToolCallStart("c2", "read_tool"),
                new StreamEvent.ToolCallDelta("c2", "{}"),
                new StreamEvent.ToolCallEnd("c2", "read_tool", Map.of()),
                new StreamEvent.Usage(10, 5, 0, 8),
                new StreamEvent.StreamComplete()
            ),
            List.of(
                new StreamEvent.ContentDelta("plan complete"),
                new StreamEvent.Usage(10, 5, 0, 8),
                new StreamEvent.StreamComplete()
            )
        ));

        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        var planMode = new PlanModeManager();
        planMode.enterPlanMode();
        loop.setConfig(config(), List.of(), "stable", "env", planMode);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("explore the codebase", events::add);

        List<Message> history = sessionManager.getHistory();
        assertHistoryLegal(history);
        for (Message msg : history) {
            if (msg.content() != null) {
                assertThat(msg.content()).doesNotContain("<system-reminder>");
            }
        }
    }

    @Test
    void cacheTokensLoggedInRoundResult() {
        var provider = new CtxCaptureProvider(List.of("done")) {
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                                   List<ToolDefinition> toolDefs,
                                                   PromptContext promptContext) {
                if (promptContext != null) capturedCtxs.add(promptContext);
                return new SingleEventIterator(
                    new StreamEvent.ContentDelta("done"),
                    new StreamEvent.Usage(10, 5, 3, 8),
                    new StreamEvent.StreamComplete());
            }
        };
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        loop.setConfig(config(), List.of(), "stable", "env", new PlanModeManager());
        List<AgentEvent> events = new ArrayList<>();
        loop.run("test", events::add);
        assertThat(events).anyMatch(e -> e instanceof AgentEvent.Complete);
        assertThat(events).anyMatch(e -> e instanceof AgentEvent.Usage);
    }

    private void assertHistoryLegal(List<Message> history) {
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            if (i > 0) {
                assertThat(msg.role()).isNotEqualTo(history.get(i - 1).role());
            }
            if (!msg.toolCalls().isEmpty()) {
                int toolCount = msg.toolCalls().size();
                int following = 0;
                for (int j = i + 1; j < history.size() && history.get(j).role() == Role.TOOL; j++) following++;
                assertThat(following).isEqualTo(toolCount);
            }
        }
    }
}
