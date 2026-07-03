package com.lavendercode.core.prompt;

import com.lavendercode.core.tool.ToolDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolDescriptionEnhancerTest {
    @Test
    void editFileDescriptionContainsReadBeforeEdit() {
        var tools = List.of(new ToolDefinition("edit_file", "Edit a file.", Map.of()));
        var enhanced = ToolDescriptionEnhancer.enhance(tools);
        assertThat(enhanced.get(0).description()).contains("read the file before edit");
    }

    @Test
    void executeCommandDescriptionContainsPreferSpecialized() {
        var tools = List.of(new ToolDefinition("execute_command", "Run a command.", Map.of()));
        var enhanced = ToolDescriptionEnhancer.enhance(tools);
        assertThat(enhanced.get(0).description()).contains("Prefer specialized tools");
    }

    @Test
    void otherToolsUnchanged() {
        var tools = List.of(new ToolDefinition("read_file", "Read a file.", Map.of()));
        var enhanced = ToolDescriptionEnhancer.enhance(tools);
        assertThat(enhanced.get(0).description()).isEqualTo("Read a file.");
    }

    @Test
    void emptyListReturnsEmpty() {
        assertThat(ToolDescriptionEnhancer.enhance(List.of())).isEmpty();
    }

    @Test
    void nullListReturnsEmpty() {
        assertThat(ToolDescriptionEnhancer.enhance(null)).isEmpty();
    }
}
