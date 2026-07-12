package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReActLoopProtocolTest {

    @TempDir
    Path projectRoot;

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
        ToolRegistry.register(new SuccessTool("read_file"));
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void shouldBehaveIdenticallyAcrossProviders() {
        StreamEvent[] round1Events = {
            new StreamEvent.ToolCallStart("c1", "read_file"),
            new StreamEvent.ToolCallDelta("c1", "{}"),
            new StreamEvent.ToolCallEnd("c1", "read_file", Map.of()),
            new StreamEvent.StreamComplete()
        };
        StreamEvent[] round2Events = {
            new StreamEvent.ContentDelta("done"),
            new StreamEvent.StreamComplete()
        };

        // Run with mock Anthropic provider
        var anthropicProvider = mock(LlmProvider.class);
        when(anthropicProvider.protocol()).thenReturn("anthropic");
        var iter1a = mockIter(round1Events);
        var iter2a = mockIter(round2Events);
        when(anthropicProvider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1a).thenReturn(iter2a);

        var anthropicEvents = runLoop(anthropicProvider);

        // Run with mock OpenAI provider — same events
        var openaiProvider = mock(LlmProvider.class);
        when(openaiProvider.protocol()).thenReturn("openai");
        var iter1b = mockIter(round1Events);
        var iter2b = mockIter(round2Events);
        when(openaiProvider.streamChat(anyList(), any(), anyList()))
            .thenReturn(iter1b).thenReturn(iter2b);

        var openaiEvents = runLoop(openaiProvider);

        // Assert identical event types
        assertThat(anthropicEvents.stream().map(Object::getClass).toList())
            .isEqualTo(openaiEvents.stream().map(Object::getClass).toList());
    }

    private List<AgentEvent> runLoop(LlmProvider provider) {
        var session = new InMemorySessionManager();
        var batchExec = PermissionTestSupport.bypassExecutor(30, 120, projectRoot);
        var tokens = new TokenAccumulator();
        var loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3, com.lavendercode.core.context.NoOpContextManager.INSTANCE);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("test", events::add);
        return events;
    }

    private StreamEventIterator mockIter(StreamEvent... events) {
        var iter = mock(StreamEventIterator.class);
        if (events.length == 0) {
            when(iter.hasNext()).thenReturn(false);
            return iter;
        }
        var hasNextCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(iter.hasNext()).thenAnswer(inv -> hasNextCount.getAndIncrement() < events.length);
        var nextCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(iter.next()).thenAnswer(inv -> events[nextCount.getAndIncrement()]);
        return iter;
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
