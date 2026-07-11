package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.ToolParameterSchema;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpSchemaConverter {
    private McpSchemaConverter() {}

    public static ToolParameterSchema toParameterSchema(McpSchema.JsonSchema inputSchema) {
        if (inputSchema == null) {
            return emptyObjectSchema();
        }
        return toParameterSchema(inputSchema.type(), inputSchema.properties(), inputSchema.required());
    }

    public static ToolParameterSchema toParameterSchema(Map<String, Object> schemaMap) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            return emptyObjectSchema();
        }
        String type = schemaMap.get("type") instanceof String s ? s : "object";
        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
            schemaMap.get("properties") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked")
        List<String> required =
            schemaMap.get("required") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return toParameterSchema(type, properties, required);
    }

    private static ToolParameterSchema toParameterSchema(
            String type, Map<String, Object> properties, List<String> required) {
        Map<String, ToolParameterSchema.PropertyDef> props = new LinkedHashMap<>();
        for (var entry : properties.entrySet()) {
            props.put(entry.getKey(), toPropertyDef(entry.getValue()));
        }
        List<String> req = required == null ? List.of() : List.copyOf(required);
        return new ToolParameterSchema(type == null || type.isBlank() ? "object" : type, props, req);
    }

    private static ToolParameterSchema.PropertyDef toPropertyDef(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new ToolParameterSchema.PropertyDef("string", "", null, null);
        }
        String type = map.get("type") instanceof String s ? s : "string";
        String description = map.get("description") instanceof String s ? s : "";
        List<String> enumValues = null;
        if (map.get("enum") instanceof List<?> list) {
            enumValues = list.stream().map(String::valueOf).toList();
        }
        return new ToolParameterSchema.PropertyDef(type, description, enumValues, null);
    }

    private static ToolParameterSchema emptyObjectSchema() {
        return new ToolParameterSchema("object", Map.of(), List.of());
    }
}
