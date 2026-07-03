package com.lavendercode.core.prompt;

import com.lavendercode.core.tool.ToolDefinition;
import java.util.List;

public class ToolDescriptionEnhancer {
    public static List<ToolDefinition> enhance(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        return tools.stream().map(t -> {
            String desc = t.description();
            if ("edit_file".equals(t.name())) {
                desc += "\n\nIMPORTANT: Always read the file before editing it.";
            }
            if ("execute_command".equals(t.name())) {
                desc += "\n\nIMPORTANT: Prefer specialized tools over shell commands.";
            }
            return new ToolDefinition(t.name(), desc, t.parameters());
        }).toList();
    }
}
