package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.List;

public final class PermissionModeManager {
    private PermissionMode mode;
    private boolean coordinatorMode;

    public PermissionModeManager(PermissionMode initial) {
        this.mode = initial;
    }

    public void setCoordinatorMode(boolean coordinatorMode) {
        this.coordinatorMode = coordinatorMode;
    }

    public boolean isCoordinatorMode() {
        return coordinatorMode;
    }

    public void cycleMode() {
        if (coordinatorMode) {
            return; // Coordinator 进程内不可切换解锁写权限
        }
        mode = switch (mode) {
            case DEFAULT -> PermissionMode.ACCEPT_EDITS;
            case ACCEPT_EDITS -> PermissionMode.PLAN;
            case PLAN -> PermissionMode.BYPASS_PERMISSIONS;
            case BYPASS_PERMISSIONS -> PermissionMode.DEFAULT;
            case DONT_ASK -> PermissionMode.DEFAULT;
        };
    }

    public void enterPlanMode() {
        if (!coordinatorMode) {
            mode = PermissionMode.PLAN;
        }
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

    public String displayLabel() {
        return coordinatorMode ? "COORDINATOR" : mode.label();
    }

    public List<ToolDefinition> getToolDefinitions(boolean toolSystemEnabled) {
        if (!toolSystemEnabled) {
            return List.of();
        }
        List<ToolDefinition> defs = isPlanMode()
            ? ToolRegistry.exportReadOnly()
            : ToolRegistry.export();
        if (coordinatorMode) {
            var allowed = new java.util.HashSet<>(com.lavendercode.core.coordinator.Coordinator.ALLOWED_TOOLS);
            return defs.stream().filter(d -> allowed.contains(d.name())).toList();
        }
        // 主会话默认隐藏队员协作五件套（TeamCreate/Delete 保留）
        var hide = new java.util.HashSet<>(com.lavendercode.core.subagent.ToolFilter.TEAM_COLLAB_TOOLS);
        return defs.stream().filter(d -> !hide.contains(d.name())).toList();
    }
}
