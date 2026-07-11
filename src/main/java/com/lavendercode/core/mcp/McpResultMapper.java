package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;

public final class McpResultMapper {
    private static final ThreadLocal<Boolean> WARNED_NON_TEXT = ThreadLocal.withInitial(() -> false);

    private McpResultMapper() {}

    public static ToolResult toToolResult(String registryName, McpSchema.CallToolResult result) {
        WARNED_NON_TEXT.set(false);
        StringBuilder text = new StringBuilder();
        List<McpSchema.Content> blocks = result.content() == null ? List.of() : result.content();
        for (McpSchema.Content block : blocks) {
            if (block instanceof McpSchema.TextContent textContent) {
                if (textContent.text() != null) {
                    text.append(textContent.text());
                }
            } else if (!WARNED_NON_TEXT.get()) {
                System.err.println(
                    "WARN: MCP tool '" + registryName + "' returned non-text content; discarded");
                WARNED_NON_TEXT.set(true);
            }
        }
        String content = text.toString();
        if (Boolean.TRUE.equals(result.isError())) {
            return ToolResult.error("MCP_TOOL_ERROR", "MCP 工具返回错误·" + registryName, content);
        }
        return ToolResult.success(registryName, content);
    }
}
