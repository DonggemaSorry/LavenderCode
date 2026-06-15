package com.lavendercode.core.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SseEventParser {

    public static List<String> parseStream(InputStream inputStream) {
        List<String> events = new ArrayList<>();
        StringBuilder dataBuffer = new StringBuilder();
        boolean hasData = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (hasData) {
                        events.add(dataBuffer.toString().trim());
                        dataBuffer.setLength(0);
                        hasData = false;
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
                // Ignore lines starting with ":" (comments) or "event:" (event type)
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SSE stream", e);
        }

        return events;
    }
}
