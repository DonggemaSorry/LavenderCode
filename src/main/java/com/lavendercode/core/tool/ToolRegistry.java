package com.lavendercode.core.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {
    private static final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public static void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public static void unregister(String name) {
        tools.remove(name);
    }

    public static Tool get(String name) {
        return tools.get(name);
    }

    public static boolean has(String name) {
        return tools.containsKey(name);
    }

    public static int size() {
        return tools.size();
    }

    public static void clear() {
        tools.clear();
    }

    public static List<ToolDefinition> export() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            defs.add(toDefinition(tool));
        }
        return defs;
    }

    private static ToolDefinition toDefinition(Tool tool) {
        ToolParameterSchema schema = tool.parameters();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", schema.type());

        Map<String, Object> props = new LinkedHashMap<>();
        for (var entry : schema.properties().entrySet()) {
            Map<String, Object> propDef = new LinkedHashMap<>();
            propDef.put("type", entry.getValue().type());
            propDef.put("description", entry.getValue().description());
            if (entry.getValue().enumValues() != null && !entry.getValue().enumValues().isEmpty()) {
                propDef.put("enum", entry.getValue().enumValues());
            }
            props.put(entry.getKey(), propDef);
        }
        params.put("properties", props);
        if (schema.required() != null && !schema.required().isEmpty()) {
            params.put("required", schema.required());
        }
        return new ToolDefinition(tool.name(), tool.description(), params);
    }
}
