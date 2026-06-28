package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionTest {
    @Test
    void createWithAllFields() {
        var td = new ToolDefinition("read_file", "Reads a file", Map.of("type", "object"));
        assertThat(td.name()).isEqualTo("read_file");
        assertThat(td.description()).isEqualTo("Reads a file");
        assertThat(td.parameters()).containsEntry("type", "object");
    }
}
