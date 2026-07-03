package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RoundCollectorTest {
    @Test
    void shouldPushContentRealtimeAndAccumulateFullText() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, true, true, true, false);
        when(iter.next()).thenReturn(
            new StreamEvent.ContentDelta("Hello "),
            new StreamEvent.ContentDelta("World"),
            new StreamEvent.StreamComplete(),
            new StreamEvent.StreamComplete()
        );
        List<String> pushed = new ArrayList<>();
        var rc = new RoundCollector((AgentEvent e) -> {
            if (e instanceof AgentEvent.Content c) pushed.add(c.text());
        });
        RoundResult result = rc.consume(iter, new AtomicBoolean(false));
        assertThat(pushed).containsExactly("Hello ", "World"); // 实时推送
        assertThat(result.fullText()).isEqualTo("Hello World");   // 累积完整
    }

    @Test
    void shouldAccumulateToolCallFromFragments() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, true, true, true, true, false);
        when(iter.next()).thenReturn(
            new StreamEvent.ToolCallStart("call_1", "read_file"),
            new StreamEvent.ToolCallDelta("call_1", "{\"path\":\"test"),
            new StreamEvent.ToolCallDelta("call_1", ".txt\"}"),
            new StreamEvent.ToolCallEnd("call_1", "read_file", Map.of()),
            new StreamEvent.StreamComplete()
        );
        List<AgentEvent> events = new ArrayList<>();
        var rc = new RoundCollector(events::add);
        RoundResult result = rc.consume(iter, new AtomicBoolean(false));
        assertThat(result.toolCalls()).hasSize(1);
        ToolCall tc = result.toolCalls().get(0);
        assertThat(tc.name()).isEqualTo("read_file");
        assertThat(tc.parameters()).containsEntry("path", "test.txt"); // 完整拼接
    }

    @Test
    void shouldStopOnCancel() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, true);
        var cancelFlag = new AtomicBoolean(false);
        when(iter.next()).thenAnswer(inv -> {
            cancelFlag.set(true);
            return new StreamEvent.ContentDelta("partial");
        });
        List<AgentEvent> events = new ArrayList<>();
        var rc = new RoundCollector(events::add);
        RoundResult result = rc.consume(iter, cancelFlag);
        assertThat(result.fullText()).isEqualTo("partial"); // only first delta
    }

    @Test
    void shouldExtractCacheTokensFromUsage() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, true, false);
        when(iter.next()).thenReturn(
            new StreamEvent.Usage(10, 5, 3, 8),
            new StreamEvent.StreamComplete()
        );
        List<AgentEvent> events = new ArrayList<>();
        var rc = new RoundCollector(events::add);
        RoundResult result = rc.consume(iter, new AtomicBoolean(false));
        assertThat(result.cacheCreationTokens()).isEqualTo(3);
        assertThat(result.cacheReadTokens()).isEqualTo(8);
    }
}
