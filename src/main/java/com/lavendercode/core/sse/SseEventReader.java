package com.lavendercode.core.sse;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * Incrementally reads SSE {@code data:} payloads from an HTTP response body.
 * One blank line delimits each event; any trailing partial event is flushed at EOF.
 */
public final class SseEventReader implements Closeable {

    private final BufferedReader reader;
    private final StringBuilder dataBuffer = new StringBuilder();
    private boolean hasData;
    private String pending;
    private boolean eof;

    public SseEventReader(InputStream inputStream) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    public boolean hasNext() {
        if (pending != null) {
            return true;
        }
        fillPending();
        return pending != null;
    }

    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String event = pending;
        pending = null;
        return event;
    }

    private void fillPending() {
        if (eof || pending != null) {
            return;
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (hasData) {
                        pending = dataBuffer.toString().trim();
                        dataBuffer.setLength(0);
                        hasData = false;
                        return;
                    }
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5);
                    if (!data.startsWith(" ")) {
                        data = " " + data;
                    }
                    data = data.substring(1);
                    if (!data.isEmpty()) {
                        if (hasData) {
                            dataBuffer.append("\n");
                        }
                        dataBuffer.append(data);
                        hasData = true;
                    }
                }
            }
            eof = true;
            if (hasData) {
                pending = dataBuffer.toString().trim();
                dataBuffer.setLength(0);
                hasData = false;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SSE stream", e);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
