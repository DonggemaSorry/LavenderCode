package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class McpResultMapperTest {
    @Test
    void concatenatesTextBlocks() {
        var result = new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent("hello"), new McpSchema.TextContent(" world")),
            false,
            null,
            null);
        ToolResult tr = McpResultMapper.toToolResult("mcp__s__t", result);
        assertThat(tr.success()).isTrue();
        assertThat(tr.content()).isEqualTo("hello world");
    }

    @Test
    void mapsIsErrorToToolResultError() {
        var result = new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("boom")), true, null, null);
        ToolResult tr = McpResultMapper.toToolResult("mcp__s__t", result);
        assertThat(tr.success()).isFalse();
        assertThat(tr.errorCategory()).isEqualTo("MCP_TOOL_ERROR");
    }
}
