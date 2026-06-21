package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.StreamEventIterator;
import okhttp3.Call;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestContext {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Call call;
    private volatile StreamEventIterator iterator;

    public RequestContext(Call call, StreamEventIterator iterator) {
        this.call = call;
        this.iterator = iterator;
    }

    public boolean isCancelled() { return cancelled.get(); }

    synchronized void bind(StreamEventIterator iterator, Call call) {
        this.iterator = iterator;
        this.call = call;
        if (cancelled.get()) {
            cancelResources();
        }
    }

    public void cancel() {
        cancelled.set(true);
        cancelResources();
    }

    private void cancelResources() {
        Call activeCall = call;
        if (activeCall != null) {
            activeCall.cancel();
        }
        StreamEventIterator activeIterator = iterator;
        if (activeIterator != null) {
            activeIterator.close();
        }
    }
}
