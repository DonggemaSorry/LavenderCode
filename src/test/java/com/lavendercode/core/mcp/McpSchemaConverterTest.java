package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.ToolParameterSchema;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class McpSchemaConverterTest {
    @Test
    void convertsObjectSchemaWithProperties() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of("q", Map.of("type", "string", "description", "query")),
            "required", List.of("q"));
        ToolParameterSchema params = McpSchemaConverter.toParameterSchema(schema);
        assertThat(params.type()).isEqualTo("object");
        assertThat(params.properties()).containsKey("q");
        assertThat(params.required()).containsExactly("q");
    }

    @Test
    void convertsJsonSchemaRecord() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object",
            Map.of("q", Map.of("type", "string", "description", "query")),
            List.of("q"),
            null,
            Map.of(),
            Map.of());
        ToolParameterSchema params = McpSchemaConverter.toParameterSchema(schema);
        assertThat(params.properties()).containsKey("q");
    }
}
