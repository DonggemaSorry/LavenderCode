package com.lavendercode.core.tool;

import java.util.Map;

public interface Tool {
    String name();
    String description();
    ToolParameterSchema parameters();
    ToolResult execute(Map<String, Object> params);

    default boolean isReadOnly() { return false; }
}
