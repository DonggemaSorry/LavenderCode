package com.lavendercode.core.sse;

import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import okhttp3.Call;
import okhttp3.Response;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Lazily parses SSE payloads into {@link StreamEvent}s while the HTTP body is still open.
 */
public final class SseStreamEventIterator implements StreamEventIterator {

    private final SseEventReader sseReader;
    private final Response response;
    private final Call call;
    private final Function<String, StreamEvent> parser;
    private StreamEvent nextEvent;
    private boolean closed;

    public SseStreamEventIterator(SseEventReader sseReader,
                                    Response response,
                                    Call call,
                                    Function<String, StreamEvent> parser) {
        this.sseReader = sseReader;
        this.response = response;
        this.call = call;
        this.parser = parser;
    }

    public Call call() {
        return call;
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        if (nextEvent != null) {
            return true;
        }
        advance();
        return nextEvent != null;
    }

    @Override
    public StreamEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        StreamEvent event = nextEvent;
        nextEvent = null;
        return event;
    }

    private void advance() {
        while (!closed && nextEvent == null && sseReader.hasNext()) {
            String data = sseReader.next();
            nextEvent = parser.apply(data);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            sseReader.close();
        } catch (IOException ignored) {
        }
        if (response != null) {
            response.close();
        }
    }
}
