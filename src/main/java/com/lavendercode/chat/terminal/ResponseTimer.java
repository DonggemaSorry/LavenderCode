package com.lavendercode.chat.terminal;

/**
 * Nanosecond-precision response timer. Thread-safe via volatile fields.
 */
public final class ResponseTimer {
    private volatile long startNanos;
    private volatile long stopNanos;

    public ResponseTimer() {
        this.startNanos = 0;
        this.stopNanos = 0;
    }

    public void start() {
        this.startNanos = System.nanoTime();
    }

    public void stop() {
        this.stopNanos = System.nanoTime();
    }

    public long elapsedSeconds() {
        long end = stopNanos > 0 ? stopNanos : System.nanoTime();
        if (startNanos == 0) return 0;
        return (end - startNanos) / 1_000_000_000;
    }
}
