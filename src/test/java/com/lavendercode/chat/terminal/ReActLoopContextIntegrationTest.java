package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.context.CompactResult;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReActLoopContextIntegrationTest {
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
    }

    private StreamEventIterator mockIter(StreamEvent... events) {
        StreamEventIterator iter = mock(StreamEventIterator.class);
        if (events.length == 0) {
            when(iter.hasNext()).thenReturn(false);
            return iter;
        }
        AtomicInteger hasNextCount = new AtomicInteger(0);
        when(iter.hasNext()).thenAnswer(inv -> hasNextCount.getAndIncrement() < events.length);
        AtomicInteger nextCount = new AtomicInteger(0);
        when(iter.next()).thenAnswer(inv -> events[nextCount.getAndIncrement()]);
        return iter;
    }

    @Test
    void emergencyCompactRetriesStreamOnceOnPromptTooLong() {
        ContextManager ctx = mock(ContextManager.class);
        when(ctx.isPromptTooLong(anyString())).thenReturn(true);
        when(ctx.runCompaction(eq(CompactTrigger.EMERGENCY), anyList()))
            .thenReturn(CompactResult.ok(90_000, 40_000));

        StreamEventIterator ptl = mockIter(new StreamEvent.StreamError("prompt_too_long", 400));
        StreamEventIterator ok = mockIter(
            new StreamEvent.ContentDelta("Done"),
            new StreamEvent.StreamComplete());
        when(provider.streamChat(anyList(), any(), anyList())).thenReturn(ptl).thenReturn(ok);

        ReActLoop loop = new ReActLoop(provider, session, batchExec, tokens, 10, 3, ctx);
        List<AgentEvent> events = new ArrayList<>();
        loop.run("hello", events::add);

        verify(ctx, times(1)).runCompaction(CompactTrigger.EMERGENCY, List.of());
        verify(ctx, times(1)).resetAnchor();
        verify(provider, times(2)).streamChat(anyList(), any(), anyList());
        assertThat(events).filteredOn(e -> e instanceof AgentEvent.Complete).hasSize(1);
    }
}
