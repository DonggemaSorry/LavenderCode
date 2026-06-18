package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaBufferTest {

    private ScheduledExecutorService scheduler;
    private BlockingQueue<RenderEvent> renderQueue;
    private DeltaBuffer buffer;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        renderQueue = new LinkedBlockingQueue<>();
        buffer = new DeltaBuffer(scheduler, renderQueue);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void singleDeltaShouldFlushViaTimer() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "hello", 0));
        RenderEvent event = renderQueue.poll(200, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) event).text()).isEqualTo("hello");
    }

    @Test
    void adjacentContentDeltasShouldMerge() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "Hel", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "lo", 0));
        RenderEvent event = renderQueue.poll(200, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) event).text()).isEqualTo("Hello");
    }

    @Test
    void forceFlushShouldDrainImmediately() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "fast", 0));
        buffer.forceFlush();
        RenderEvent event = renderQueue.poll(10, TimeUnit.MILLISECONDS);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
    }

    @Test
    void completeShouldFlushFirstThenFinalize() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "text", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.STREAM_COMPLETE, "", 0));
        buffer.forceFlush();
        RenderEvent e1 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e2 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(e1).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(e2).isInstanceOf(RenderEvent.FinalizeMessage.class);
    }

    @Test
    void contentThinkContentShouldRetainArrivalOrder() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "A", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.THINK_DELTA, "think", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "B", 0));
        buffer.forceFlush();
        RenderEvent e1 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e2 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e3 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(e1).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) e1).text()).isEqualTo("A");
        assertThat(e2).isInstanceOf(RenderEvent.ThinkDelta.class);
        assertThat(((RenderEvent.ThinkDelta) e2).text()).isEqualTo("think");
        assertThat(e3).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) e3).text()).isEqualTo("B");
    }

    @Test
    void forceFlushShouldHandleConcurrentAppends() throws Exception {
        for (int i = 0; i < 100; i++) {
            buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, String.valueOf(i), 0));
        }
        buffer.forceFlush();
        int count = 0;
        while (renderQueue.poll(50, TimeUnit.MILLISECONDS) != null) count++;
        assertThat(count).isEqualTo(1); // all merged into one
    }

    @Test
    void streamErrorShouldBecomeAddSystemMessage() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.STREAM_ERROR, "fail", 500));
        buffer.forceFlush();
        RenderEvent event = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddSystemMessage.class);
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("fail");
    }
}
