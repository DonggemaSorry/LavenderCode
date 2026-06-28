package com.lavendercode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates streaming JSON parameter fragments by toolCallId, producing complete ToolCall objects.
 * Usage:
 *   accumulator.start("toolu_01", "read_file");
 *   accumulator.append("toolu_01", "{\"path\":\"/");
 *   accumulator.append("toolu_01", "config.yaml\"}");
 *   ToolCall call = accumulator.complete("toolu_01");
 */
public class ToolCallAccumulator {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Accum> accumulators = new LinkedHashMap<>();

    public void start(String toolCallId, String toolName) {
        accumulators.put(toolCallId, new Accum(toolCallId, toolName));
    }

    public void append(String toolCallId, String jsonFragment) {
        Accum acc = accumulators.get(toolCallId);
        if (acc != null) {
            acc.jsonBuffer.append(jsonFragment);
        }
    }

    public ToolCall complete(String toolCallId) {
        Accum acc = accumulators.remove(toolCallId);
        if (acc == null) return null;
        try {
            Map<String, Object> params = mapper.readValue(
                acc.jsonBuffer.toString(),
                new TypeReference<Map<String, Object>>() {}
            );
            return new ToolCall(acc.toolCallId, acc.toolName, params);
        } catch (Exception e) {
            return new ToolCall(acc.toolCallId, acc.toolName, Map.of())
                .withParseError(e.getMessage());
        }
    }

    public List<String> pendingIds() {
        return new ArrayList<>(accumulators.keySet());
    }

    public boolean isEmpty() {
        return accumulators.isEmpty();
    }

    public void clear() {
        accumulators.clear();
    }

    private static class Accum {
        final String toolCallId;
        final String toolName;
        final StringBuilder jsonBuffer = new StringBuilder();
        Accum(String id, String name) { this.toolCallId = id; this.toolName = name; }
    }
}
