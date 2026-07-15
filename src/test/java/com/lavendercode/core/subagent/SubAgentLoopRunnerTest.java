package com.lavendercode.core.subagent;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class SubAgentLoopRunnerTest {

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
        ToolRegistry.register(new Tool() {
            public String name() { return "read_file"; }
            public String description() { return "d"; }
            public boolean isReadOnly() { return true; }
            public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }
            public ToolResult execute(Map<String, Object> p) {
                return ToolResult.success("read", "content");
            }
        });
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void runToCompletionReturnsFinalAssistantText() {
        AtomicInteger round = new AtomicInteger();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String protocol() { return "test"; }

            @Override
            public StreamEventIterator streamChat(
                    java.util.List<com.lavendercode.core.provider.Message> history,
                    LlmConfig config) {
                int n = round.incrementAndGet();
                if (n == 1) {
                    return eventIterator(
                        new StreamEvent.ToolCallStart("tc1", "read_file"),
                        new StreamEvent.ToolCallEnd("tc1", "read_file", Map.of("path", "a.txt")),
                        new StreamEvent.StreamComplete());
                }
                return eventIterator(
                    new StreamEvent.ContentDelta("Done exploring."),
                    new StreamEvent.StreamComplete());
            }
        };

        var session = new InMemorySessionManager();
        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("test", "openai", "m", "http://x", "k", null)),
            new Options());
        var runner = new SubAgentLoopRunner(
            provider, config,
            (calls, flag) -> List.of(ToolResult.success("read", "content")),
            5);
        List<ToolDefinition> toolDefs = ToolFilter.filterDefinitions(
            new AgentDefinition("explore", "d", List.of("read_file"), List.of(),
                "inherit", 5, null, false, "body", AgentCatalog.Source.BUILTIN),
            false, false);
        String result = runner.runToCompletion(
            session, toolDefs, "Find bugs in a.txt", new AtomicBoolean(false));
        assertThat(result).isEqualTo("Done exploring.");
    }

    private static StreamEventIterator eventIterator(StreamEvent... events) {
        List<StreamEvent> list = List.of(events);
        return new StreamEventIterator() {
            private int idx;
            public boolean hasNext() { return idx < list.size(); }
            public StreamEvent next() { return list.get(idx++); }
            public void close() {}
        };
    }
}
