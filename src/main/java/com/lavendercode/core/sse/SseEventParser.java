package com.lavendercode.core.sse;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SseEventParser {

    public static List<String> parseStream(InputStream inputStream) {
        List<String> events = new ArrayList<>();
        try (SseEventReader reader = new SseEventReader(inputStream)) {
            while (reader.hasNext()) {
                events.add(reader.next());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read SSE stream", e);
        }
        return events;
    }
}
