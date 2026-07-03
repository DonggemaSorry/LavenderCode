package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.List;

public class PlanModeManager {
    public enum Mode { FULL, PLAN }
    private Mode mode = Mode.FULL;

    public void enterPlanMode() { mode = Mode.PLAN; }
    public void exitToDo() { mode = Mode.FULL; }
    public boolean isPlanMode() { return mode == Mode.PLAN; }

    public List<ToolDefinition> getToolDefinitions() {
        return mode == Mode.PLAN ? ToolRegistry.exportReadOnly() : ToolRegistry.export();
    }

}
