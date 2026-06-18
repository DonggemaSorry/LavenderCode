package com.lavendercode.chat.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe delta buffer that batches incoming events on a 50ms timer.
 * forceFlush() drains immediately. Uses snapshot-then-clear for data safety.
 */
public class DeltaBuffer {

    public record BufferedEvent(Type type, String text, int statusCode) {

        public enum Type {
            CONTENT_DELTA, THINK_DELTA,
            STREAM_COMPLETE, STREAM_ERROR,
            USER_MESSAGE, SYSTEM_MESSAGE
        }

        public BufferedEvent(Type type, String text, int statusCode) {
            this.type = type;
            this.text = text != null ? text : "";
            this.statusCode = statusCode;
        }

        public RenderEvent toRenderEvent() {
            return switch (type) {
                case STREAM_COMPLETE -> new RenderEvent.FinalizeMessage();
                case STREAM_ERROR    -> new RenderEvent.AddSystemMessage("[Error] " + text);
                case USER_MESSAGE    -> new RenderEvent.AddUserMessage(text);
                case SYSTEM_MESSAGE  -> new RenderEvent.AddSystemMessage(text);
                default -> throw new IllegalStateException("Cannot convert " + type);
            };
        }
    }

    private final List<BufferedEvent> events = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;
    private volatile boolean flushing;
    private final Object lock = new Object();
    private final ScheduledExecutorService timerScheduler;
    private final BlockingQueue<RenderEvent> renderQueue;

    public DeltaBuffer(ScheduledExecutorService timerScheduler,
                       BlockingQueue<RenderEvent> renderQueue) {
        this.timerScheduler = timerScheduler;
        this.renderQueue = renderQueue;
    }

    public void append(BufferedEvent event) {
        synchronized (lock) {
            events.add(event);
            scheduleIfNeeded();
        }
    }

    public void forceFlush() {
        cancelTimer();
        doFlush();
        while (hasPending()) {
            cancelTimer();
            doFlush();
        }
    }

    private void scheduleIfNeeded() {
        if (scheduledFlush == null || scheduledFlush.isDone()) {
            scheduledFlush = timerScheduler.schedule(this::doFlush, 50, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelTimer() {
        synchronized (lock) {
            if (scheduledFlush != null && !scheduledFlush.isDone()) {
                scheduledFlush.cancel(false);
            }
        }
    }

    private boolean hasPending() {
        synchronized (lock) { return !events.isEmpty(); }
    }

    private void doFlush() {
        List<BufferedEvent> snapshot;
        synchronized (lock) {
            if (flushing) return;
            flushing = true;
            snapshot = new ArrayList<>(events);
            events.clear();
        }

        try {
            List<RenderEvent> batch = buildBatch(snapshot);
            for (RenderEvent e : batch) {
                renderQueue.put(e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            flushing = false;
        }
    }

    private List<RenderEvent> buildBatch(List<BufferedEvent> snapshot) {
        List<RenderEvent> result = new ArrayList<>();
        StringBuilder textBuf = new StringBuilder();
        StringBuilder thinkBuf = new StringBuilder();
        BufferedEvent.Type lastType = null;

        for (BufferedEvent e : snapshot) {
            if (e.type != lastType && lastType != null) {
                flushBuffer(result, lastType, textBuf, thinkBuf);
            }
            switch (e.type) {
                case CONTENT_DELTA -> textBuf.append(e.text);
                case THINK_DELTA   -> thinkBuf.append(e.text);
                case STREAM_COMPLETE, STREAM_ERROR, USER_MESSAGE, SYSTEM_MESSAGE -> {
                    flushBuffer(result, lastType, textBuf, thinkBuf);
                    result.add(e.toRenderEvent());
                }
            }
            lastType = e.type;
        }
        flushBuffer(result, lastType, textBuf, thinkBuf);
        return result;
    }

    private void flushBuffer(List<RenderEvent> result, BufferedEvent.Type type,
                             StringBuilder textBuf, StringBuilder thinkBuf) {
        if (type == BufferedEvent.Type.CONTENT_DELTA && textBuf.length() > 0) {
            result.add(new RenderEvent.AppendToMessage(textBuf.toString()));
            textBuf.setLength(0);
        }
        if (type == BufferedEvent.Type.THINK_DELTA && thinkBuf.length() > 0) {
            result.add(new RenderEvent.ThinkDelta(thinkBuf.toString()));
            thinkBuf.setLength(0);
        }
    }
}
