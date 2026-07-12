package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.core.config.*;
import com.lavendercode.core.permission.*;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReActLoopPermissionTest {
    @TempDir
    Path root;

    private InMemorySessionManager sessionManager;
    private BatchingToolExecutor batchExecutor;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
        ToolRegistry.clear();
        ToolRegistry.register(new WriteStub());
        PermissionPipeline pipeline = PermissionPipeline.create(
            PermissionConfig.empty(),
            () -> PermissionMode.DEFAULT,
            (request, cancelFlag) -> HitlChoice.DENY,
            root,
            rules -> {});
        batchExecutor = new BatchingToolExecutor(30, 120, pipeline, root);
    }

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void loopContinuesAfterPermissionDeny() {
        var provider = new TwoRoundProvider();
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, new TokenAccumulator(), 10, 3, com.lavendercode.core.context.NoOpContextManager.INSTANCE);
        loop.setConfig(config(), List.of(), "stable", "env",
            new PermissionModeManager(PermissionMode.DEFAULT));

        List<AgentEvent> events = new ArrayList<>();
        loop.run("write something", events::add);

        assertThat(events.stream().filter(e -> e instanceof AgentEvent.RoundStart).count()).isEqualTo(2);
        assertThat(events.stream().anyMatch(e -> e instanceof AgentEvent.Complete)).isTrue();

        boolean hasPermissionDenied = sessionManager.getHistory().stream()
            .filter(m -> m.role() == Role.TOOL)
            .flatMap(m -> m.toolResults().stream())
            .anyMatch(r -> "PERMISSION_DENIED".equals(r.errorCategory()));
        assertThat(hasPermissionDenied).isTrue();
        assertThat(WriteStub.EXECUTED.get()).isFalse();
    }

    private static LlmConfig config() {
        return new LlmConfig(
            List.of(ProviderConfig.of("mock", "mock", "m", "http://localhost", "k", null)),
            new Options());
    }

    private static class TwoRoundProvider implements LlmProvider {
        private int round;

        @Override
        public String protocol() {
            return "mock";
        }

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
            round++;
            if (round == 1) {
                return iterator(
                    new StreamEvent.ToolCallStart("c1", "write_file"),
                    new StreamEvent.ToolCallDelta("c1", "{\"path\":\"a.txt\",\"content\":\"x\"}"),
                    new StreamEvent.ToolCallEnd("c1", "write_file",
                        Map.of("path", "a.txt", "content", "x")),
                    new StreamEvent.Usage(10, 5, 0, 0),
                    new StreamEvent.StreamComplete());
            }
            return iterator(
                new StreamEvent.ContentDelta("Done after deny."),
                new StreamEvent.Usage(5, 3, 0, 0),
                new StreamEvent.StreamComplete());
        }

        private static StreamEventIterator iterator(StreamEvent... events) {
            return new StreamEventIterator() {
                private int idx;

                @Override
                public boolean hasNext() {
                    return idx < events.length;
                }

                @Override
                public StreamEvent next() {
                    return events[idx++];
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static class WriteStub implements Tool {
        static final AtomicBoolean EXECUTED = new AtomicBoolean(false);

        @Override
        public String name() {
            return "write_file";
        }

        @Override
        public String description() {
            return "write";
        }

        @Override
        public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters) {
            EXECUTED.set(true);
            return ToolResult.success("written", "");
        }
    }
}
