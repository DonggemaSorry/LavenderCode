package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlanModeManagerTest {
    @AfterEach
    void cleanup() { ToolRegistry.clear(); }

    @Test
    void planModeShouldExportReadOnlyToolsOnly() {
        ToolRegistry.register(new ReadOnlyDummy("ro"));
        ToolRegistry.register(new WriteDummy("rw"));
        var mgr = new PlanModeManager();
        mgr.enterPlanMode();
        var defs = mgr.getToolDefinitions();
        assertThat(defs).hasSize(1).extracting(ToolDefinition::name).contains("ro");
    }

    @Test
    void doShouldRestoreAllTools() {
        ToolRegistry.register(new ReadOnlyDummy("ro"));
        ToolRegistry.register(new WriteDummy("rw"));
        var mgr = new PlanModeManager();
        mgr.enterPlanMode();
        mgr.exitToDo();
        var defs = mgr.getToolDefinitions();
        assertThat(defs).hasSize(2);
    }

    @Test
    void planThenDoShouldSwitchToolSets() {
        ToolRegistry.register(new ReadOnlyDummy("read_file"));
        ToolRegistry.register(new WriteDummy("write_file"));
        var mgr = new PlanModeManager();
        mgr.enterPlanMode();
        assertThat(mgr.getToolDefinitions()).hasSize(1); // read_file only
        mgr.exitToDo();
        assertThat(mgr.getToolDefinitions()).hasSize(2); // all tools
    }

    static class ReadOnlyDummy implements Tool {
        private final String n;
        ReadOnlyDummy(String n) { this.n = n; }
        @Override public String name() { return n; }
        @Override public String description() { return "d"; }
        @Override public ToolParameterSchema parameters() { return new ToolParameterSchema("object", Map.of(), List.of()); }
        @Override public ToolResult execute(Map<String, Object> p) { return ToolResult.success("ok", ""); }
        @Override public boolean isReadOnly() { return true; }
    }
    static class WriteDummy implements Tool {
        private final String n;
        WriteDummy(String n) { this.n = n; }
        @Override public String name() { return n; }
        @Override public String description() { return "d"; }
        @Override public ToolParameterSchema parameters() { return new ToolParameterSchema("object", Map.of(), List.of()); }
        @Override public ToolResult execute(Map<String, Object> p) { return ToolResult.success("ok", ""); }
    }
}
