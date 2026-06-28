package com.lavendercode.core.tool;

import java.util.List;
import java.util.Map;

public record ToolParameterSchema(String type, Map<String, PropertyDef> properties, List<String> required) {
    public record PropertyDef(String type, String description, List<String> enumValues, PropertyDef items) {}
}
