package com.lavendercode.core.tool;

import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ToolRegistryFilterTest {

    private static class FakeTool implements Tool {
        private final String name;
        FakeTool(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return "fake"; }
        @Override public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", java.util.Map.of(), List.of());
        }
        @Override public ToolResult execute(java.util.Map<String, Object> params) {
            return ToolResult.success("ok", "ok");
        }
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clearFilter();
        ToolRegistry.clear();
    }

    @Test
    void setFilterRestrictsExport() {
        ToolRegistry.register(new FakeTool("read_file"));
        ToolRegistry.register(new FakeTool("write_file"));
        ToolRegistry.register(new FakeTool("grep"));
        ToolRegistry.setFilter(name -> name.equals("read_file") || name.equals("grep"));
        var exported = ToolRegistry.export();
        assertThat(exported).hasSize(2);
        assertThat(exported).extracting(ToolDefinition::name)
            .containsExactlyInAnyOrder("read_file", "grep");
    }

    @Test
    void clearFilterRestoresAll() {
        ToolRegistry.register(new FakeTool("read_file"));
        ToolRegistry.register(new FakeTool("write_file"));
        ToolRegistry.setFilter(name -> name.equals("read_file"));
        ToolRegistry.clearFilter();
        assertThat(ToolRegistry.export()).hasSize(2);
    }

    @Test
    void exportReadOnlyRespectsFilter() {
        ToolRegistry.register(new FakeTool("read_file") {
            @Override public boolean isReadOnly() { return true; }
        });
        ToolRegistry.register(new FakeTool("write_file") {
            @Override public boolean isReadOnly() { return true; }
        });
        ToolRegistry.setFilter(name -> name.equals("read_file"));
        var exported = ToolRegistry.exportReadOnly();
        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).name()).isEqualTo("read_file");
    }

    @Test
    void hasIgnoresFilter() {
        ToolRegistry.register(new FakeTool("read_file"));
        ToolRegistry.setFilter(name -> false);
        assertThat(ToolRegistry.has("read_file")).isTrue();
    }
}
