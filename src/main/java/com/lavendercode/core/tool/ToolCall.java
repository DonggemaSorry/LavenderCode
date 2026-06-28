package com.lavendercode.core.tool;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> parameters, String parseError) {
    public ToolCall(String id, String name, Map<String, Object> parameters) {
        this(id, name, parameters, null);
    }

    public ToolCall withParseError(String error) {
        return new ToolCall(id, name, parameters, error);
    }
}
