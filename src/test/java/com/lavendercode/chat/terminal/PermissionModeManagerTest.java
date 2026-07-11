package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionModeManagerTest {
    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void cycleModeOrder() {
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        mgr.cycleMode();
        assertThat(mgr.getMode()).isEqualTo(PermissionMode.ACCEPT_EDITS);
        mgr.cycleMode();
        assertThat(mgr.getMode()).isEqualTo(PermissionMode.PLAN);
        mgr.cycleMode();
        assertThat(mgr.getMode()).isEqualTo(PermissionMode.BYPASS_PERMISSIONS);
        mgr.cycleMode();
        assertThat(mgr.getMode()).isEqualTo(PermissionMode.DEFAULT);
    }

    @Test
    void doExitsToDefaultNotPrevious() {
        var mgr = new PermissionModeManager(PermissionMode.ACCEPT_EDITS);
        mgr.enterPlanMode();
        mgr.exitPlanToDefault();
        assertThat(mgr.getMode()).isEqualTo(PermissionMode.DEFAULT);
    }

    @Test
    void planModeShouldExportReadOnlyToolsOnly() {
        ToolRegistry.register(new ReadOnlyDummy("ro"));
        ToolRegistry.register(new WriteDummy("rw"));
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        mgr.enterPlanMode();
        var defs = mgr.getToolDefinitions(true);
        assertThat(defs).hasSize(1).extracting(ToolDefinition::name).contains("ro");
    }

    @Test
    void doShouldRestoreAllTools() {
        ToolRegistry.register(new ReadOnlyDummy("ro"));
        ToolRegistry.register(new WriteDummy("rw"));
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        mgr.enterPlanMode();
        mgr.exitPlanToDefault();
        var defs = mgr.getToolDefinitions(true);
        assertThat(defs).hasSize(2);
    }

    @Test
    void planThenDoShouldSwitchToolSets() {
        ToolRegistry.register(new ReadOnlyDummy("read_file"));
        ToolRegistry.register(new WriteDummy("write_file"));
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        mgr.enterPlanMode();
        assertThat(mgr.getToolDefinitions(true)).hasSize(1);
        mgr.exitPlanToDefault();
        assertThat(mgr.getToolDefinitions(true)).hasSize(2);
    }

    static class ReadOnlyDummy implements Tool {
        private final String n;

        ReadOnlyDummy(String n) {
            this.n = n;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public String description() {
            return "d";
        }

        @Override
        public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> p) {
            return ToolResult.success("ok", "");
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }
    }

    static class WriteDummy implements Tool {
        private final String n;

        WriteDummy(String n) {
            this.n = n;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public String description() {
            return "d";
        }

        @Override
        public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> p) {
            return ToolResult.success("ok", "");
        }
    }
}
