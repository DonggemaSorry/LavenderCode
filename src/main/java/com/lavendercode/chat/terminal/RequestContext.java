package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.StreamEventIterator;
import okhttp3.Call;
import java.util.concurrent.atomic.AtomicBoolean;

public class RequestContext {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Call call;
    private final StreamEventIterator iterator;

    public RequestContext(Call call, StreamEventIterator iterator) {
        this.call = call;
        this.iterator = iterator;
    }

    public boolean isCancelled() { return cancelled.get(); }

    public void cancel() {
        cancelled.set(true);
        if (call != null) call.cancel();
        if (iterator != null) iterator.close();
    }
}
