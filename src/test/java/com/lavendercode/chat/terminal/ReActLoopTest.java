package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import com.lavendercode.core.permission.PermissionMode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReActLoopTest {
    SessionManager session;
    LlmProvider provider;
    BatchingToolExecutor batchExec;
    TokenAccumulator tokens;

    @TempDir
    Path projectRoot;

    @BeforeEach
    void setup() {
        session = new InMemorySessionManager();
        provider = mock(LlmProvider.class);
        batchExec = PermissionTestSupport.bypassExecutor(30, 120, projectRoot);
        tokens = new TokenAccumulator();
        ToolRegistry.clear();
    }

    private StreamEventIterator mockIter(StreamEvent... events) {
        var iter = mock(StreamEventIterator.class);
        if (events.length == 0) {
            when(iter.hasNext()).thenReturn(false);
            return iter;
        }
        // Use AtomicInteger counters for reliable sequential stubbing
        var hasNextCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(iter.hasNext()).thenAnswer(inv -> hasNextCount.getAndIncrement() < events.length);
        var nextCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(iter.next()).thenAnswer(inv -> events[nextCount.getAndIncrement()]);
        return iter;
    }

    private StreamEvent[] toolCallIter(String... names) {
        List<StreamEvent> events = new ArrayList<>();
        int i = 0;
        for (String name : names) {
            String id = "call_" + (i++);
            events.add(new StreamEvent.ToolCallStart(id, name));
            events.add(new StreamEvent.ToolCallDelta(id, "{}"));
            events.add(new StreamEvent.ToolCallEnd(id, name, Map.of()));
        }
        events.add(new StreamEvent.StreamComplete());
        return events.toArray(new StreamEvent[0]);
    }

    // AC2: natural completion
    @Test
    void shouldStopOnNaturalCompletion_Ac2() {
        var iter1 = mockIter(new StreamEvent.ContentDelta("Done!"), new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList())).thenReturn(iter1);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Complete).hasSize(1);
        assertThat(session.getHistory()).hasSize(2); // user + assistant
    }

    // AC3: max iterations
    @Test
    void shouldStopAtMaxIterations_Ac3() {
        var iter1 = mockIter(toolCallIter("fake_tool", "fake_tool"));
        var iter2 = mockIter(toolCallIter("fake_tool", "fake_tool"));
        var iter3 = mockIter(toolCallIter("fake_tool", "fake_tool"));
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2).thenReturn(iter3);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 3, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Stopped).hasSize(1);
        var stopped = (AgentEvent.Stopped) events.stream().filter(e -> e instanceof AgentEvent.Stopped).findFirst().get();
        assertThat(stopped.reason()).isEqualTo(AgentEvent.StopReason.MAX_ITERATIONS);
    }

    // AC4: consecutive unknown tools
    @Test
    void shouldStopOnConsecutiveUnknownTools_Ac4() {
        var iter1 = mockIter(toolCallIter("fake_tool"));
        var iter2 = mockIter(toolCallIter("fake_tool"));
        var iter3 = mockIter(toolCallIter("fake_tool"));
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2).thenReturn(iter3);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);
        var stopped = (AgentEvent.Stopped) events.stream().filter(e -> e instanceof AgentEvent.Stopped).findFirst().get();
        assertThat(stopped.reason()).isEqualTo(AgentEvent.StopReason.UNKNOWN_TOOLS);
    }

    // AC5: stream error recovery
    @Test
    void shouldRecoverFromStreamError_Ac5() {
        var iter1 = mockIter(new StreamEvent.StreamError("fail", 500));
        when(provider.streamChat(anyList(), any(), anyList())).thenReturn(iter1);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Error).hasSize(1);
    }

    // AC1: multi-round until no tools
    @Test
    void shouldRunMultipleRoundsUntilNoTools_Ac1() {
        ToolRegistry.register(new SuccessTool("read_file"));
        var iter1 = mockIter(
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{\"path\":\"x\"}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of("path", "x")),
            new StreamEvent.StreamComplete());
        var iter2 = mockIter(
            new StreamEvent.ContentDelta("File content is..."),
            new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.RoundStart).hasSize(2);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Complete).hasSize(1);
    }

    // AC6: event completeness
    @Test
    void shouldEmitAllEventTypes_Ac6() {
        ToolRegistry.register(new SuccessTool("read_file"));
        var iter1 = mockIter(
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of()),
            new StreamEvent.Usage(100, 50),
            new StreamEvent.StreamComplete());
        var iter2 = mockIter(new StreamEvent.ContentDelta("ok"), new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hi", events::add);
        Set<Class<?>> types = new HashSet<>();
        for (var e : events) types.add(e.getClass());
        assertThat(types).contains(
            AgentEvent.RoundStart.class, AgentEvent.Content.class,
            AgentEvent.ToolCallStart.class, AgentEvent.ToolCallEnd.class,
            AgentEvent.ToolResultReady.class, AgentEvent.Usage.class,
            AgentEvent.RoundEnd.class, AgentEvent.Complete.class);
    }

    // AC11: token accumulation across rounds
    @Test
    void shouldAccumulateTokensAcrossRounds_Ac11() {
        ToolRegistry.register(new SuccessTool("read_file"));
        var iter1 = mockIter(
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of()),
            new StreamEvent.Usage(100, 50),
            new StreamEvent.StreamComplete());
        var iter2 = mockIter(
            new StreamEvent.ContentDelta("done"),
            new StreamEvent.Usage(200, 80),
            new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hi", events::add);
        var usageEvents = events.stream().filter(e -> e instanceof AgentEvent.Usage).toList();
        var lastUsage = (AgentEvent.Usage) usageEvents.get(usageEvents.size() - 1);
        assertThat(lastUsage.inputTokens()).isEqualTo(300); // 100+200
        assertThat(lastUsage.outputTokens()).isEqualTo(130); // 50+80
    }

    // AC12: round progress
    @Test
    void shouldEmitRoundProgress_Ac12() {
        ToolRegistry.register(new SuccessTool("read_file"));
        var iter1 = mockIter(
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of()),
            new StreamEvent.StreamComplete());
        var iter2 = mockIter(
            new StreamEvent.ToolCallStart("c2", "read_file"),
            new StreamEvent.ToolCallDelta("c2", "{}"),
            new StreamEvent.ToolCallEnd("c2", "read_file", Map.of()),
            new StreamEvent.StreamComplete());
        var iter3 = mockIter(
            new StreamEvent.ContentDelta("done"),
            new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1).thenReturn(iter2).thenReturn(iter3);
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hi", events::add);
        var roundStarts = events.stream()
            .filter(e -> e instanceof AgentEvent.RoundStart)
            .map(e -> ((AgentEvent.RoundStart) e).round())
            .toList();
        assertThat(roundStarts).containsExactly(1, 2, 3);
    }

    // AC9 + AC10: cancel during tool execution, history stays legal
    @Test
    void shouldKeepHistoryLegalAfterCancel_Ac9_Ac10() throws Exception {
        ToolRegistry.register(new Tool() {
            @Override public String name() { return "read_file"; }
            @Override public String description() { return "slow"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of()); }
            @Override public ToolResult execute(Map<String, Object> p) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ToolResult.success("slow-ok", "");
            }
            @Override public boolean isReadOnly() { return true; }
        });

        var iter1 = mockIter(
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{\"path\":\"a\"}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of("path", "a")),
            new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList())).thenReturn(iter1);

        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3);
        List<AgentEvent> events = new ArrayList<>();

        // Run in background, cancel after 200ms
        Thread runThread = new Thread(() -> loop.run("hello", events::add));
        runThread.start();
        Thread.sleep(200);
        loop.cancel();
        runThread.join(5000);

        // Verify history is legal
        List<Message> history = session.getHistory();
        assertHistoryLegal(history);
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Stopped).hasSize(1);
        var stopped = (AgentEvent.Stopped) events.stream().filter(e -> e instanceof AgentEvent.Stopped).findFirst().get();
        assertThat(stopped.reason()).isEqualTo(AgentEvent.StopReason.USER_CANCELLED);
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

    // Helper tool that always succeeds
    static class SuccessTool implements Tool {
        private final String n;
        SuccessTool(String n) { this.n = n; }
        @Override public String name() { return n; }
        @Override public String description() { return "test"; }
        @Override public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), List.of());
        }
        @Override public ToolResult execute(Map<String, Object> p) {
            return ToolResult.success("ok", "content");
        }
        @Override public boolean isReadOnly() { return true; }
    }
}
