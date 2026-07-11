package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolParameterSchema;
import com.lavendercode.core.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;

public final class McpToolAdapter implements Tool {
    private final String registryName;
    private final String description;
    private final ToolParameterSchema parameters;
    private final boolean readOnly;
    private final McpSessionManager sessions;

    public McpToolAdapter(
            String registryName,
            String description,
            McpSchema.Tool remoteTool,
            boolean readOnly,
            McpSessionManager sessions) {
        this.registryName = registryName;
        this.description = description;
        this.parameters = McpSchemaConverter.toParameterSchema(remoteTool.inputSchema());
        this.readOnly = readOnly;
        this.sessions = sessions;
    }

    @Override
    public String name() {
        return registryName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ToolParameterSchema parameters() {
        return parameters;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            McpSchema.CallToolResult result = sessions.callTool(registryName, params);
            return McpResultMapper.toToolResult(registryName, result);
        } catch (Exception e) {
            return ToolResult.error("MCP_ERROR", "MCP 工具执行失败·" + registryName, e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }
}
