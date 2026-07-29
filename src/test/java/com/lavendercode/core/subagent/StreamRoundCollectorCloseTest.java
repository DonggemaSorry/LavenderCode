package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

/** 回归：任何退出路径都必须关闭流迭代器，否则泄漏 OkHttp 连接。 */
class StreamRoundCollectorCloseTest {

    @Test
    void shouldCloseIteratorOnNaturalExhaustion() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.next()).thenReturn(new StreamEvent.ContentDelta("x"));

        new StreamRoundCollector().consume(iter, new AtomicBoolean(false));

        verify(iter).close();
    }

    @Test
    void shouldCloseIteratorOnStreamComplete() {
        var iter = mock(StreamEventIterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.next()).thenReturn(new StreamEvent.StreamComplete());

        new StreamRoundCollector().consume(iter, new AtomicBoolean(false));

        verify(iter, atLeastOnce()).close();
    }
}
