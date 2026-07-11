package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.List;

public final class PermissionModeManager {
    private PermissionMode mode;

    public PermissionModeManager(PermissionMode initial) {
        this.mode = initial;
    }

    public void cycleMode() {
        mode = switch (mode) {
            case DEFAULT -> PermissionMode.ACCEPT_EDITS;
            case ACCEPT_EDITS -> PermissionMode.PLAN;
            case PLAN -> PermissionMode.BYPASS_PERMISSIONS;
            case BYPASS_PERMISSIONS -> PermissionMode.DEFAULT;
        };
    }

    public void enterPlanMode() {
        mode = PermissionMode.PLAN;
    }

    public void exitPlanToDefault() {
        mode = PermissionMode.DEFAULT;
    }

    public boolean isPlanMode() {
        return mode == PermissionMode.PLAN;
    }

    public PermissionMode getMode() {
        return mode;
    }

    public List<ToolDefinition> getToolDefinitions(boolean toolSystemEnabled) {
        if (!toolSystemEnabled) {
            return List.of();
        }
        return isPlanMode() ? ToolRegistry.exportReadOnly() : ToolRegistry.export();
    }
}
