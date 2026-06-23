package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StreamingChatServiceTest {

    @Test
    void shouldConvertStreamEventsToDeltaEvents() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next())
            .thenReturn(new StreamEvent.ContentDelta("hello"))
            .thenReturn(new StreamEvent.ContentDelta(" world"));
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai-compatible", "model", "http://localhost", "key"),
            null
        );

        List<DeltaEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        StreamingChatService service = new StreamingChatService();
        service.submit(provider, List.of(), config, delta -> {
            received.add(delta);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);

        assertThat(received).hasSize(3);
        assertThat(received.get(0)).isInstanceOf(DeltaEvent.Content.class);
        assertThat(((DeltaEvent.Content) received.get(0)).text()).isEqualTo("hello");
        assertThat(received.get(1)).isInstanceOf(DeltaEvent.Content.class);
        assertThat(received.get(2)).isInstanceOf(DeltaEvent.Complete.class);
    }

    @Test
    void shouldConvertErrorEvent() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(new StreamEvent.StreamError("fail", 500));
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        List<DeltaEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        StreamingChatService service = new StreamingChatService();
        service.submit(provider, List.of(), null, delta -> {
            received.add(delta);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);

        assertThat(received.get(0)).isInstanceOf(DeltaEvent.Error.class);
    }

    @Test
    void cancelShouldStopProcessing() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true);
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        CountDownLatch started = new CountDownLatch(1);
        StreamingChatService service = new StreamingChatService();
        RequestContext ctx = service.submit(provider, List.of(), null, delta -> {
            started.countDown();
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        });

        started.await(1, TimeUnit.SECONDS);
        assertThat(ctx.isCancelled()).isFalse();
        service.cancel(ctx);
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void shouldSkipThinkingDelta() throws Exception {
        LlmProvider mockProvider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next()).thenReturn(
            new StreamEvent.ThinkingDelta("thinking text should be discarded"),
            new StreamEvent.ContentDelta("hello")
        );
        when(mockProvider.streamChat(any(), any())).thenReturn(iterator);

        List<DeltaEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ChatService service = new StreamingChatService();
        service.submit(mockProvider, List.of(), new LlmConfig(
            List.of(new ProviderConfig(null, "openai", "test", null, "sk-test", null)),
            new Options()
        ), delta -> {
            events.add(delta);
            if (delta instanceof DeltaEvent.Complete) latch.countDown();
        });

        latch.await(2, java.util.concurrent.TimeUnit.SECONDS);

        // Thinking delta should be discarded; only Content and Complete should appear
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(DeltaEvent.Content.class);
        assertThat(((DeltaEvent.Content) events.get(0)).text()).isEqualTo("hello");
        assertThat(events.get(1)).isInstanceOf(DeltaEvent.Complete.class);
    }

    @Test
    void shutdownShouldStopIoPool() throws Exception {
        StreamingChatService service = new StreamingChatService();
        service.shutdown();
    }
}
