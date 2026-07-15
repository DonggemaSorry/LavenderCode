package com.lavendercode.core.tool;

import com.lavendercode.core.team.SharedTask;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;

public final class TeamTaskUpdateTool extends AbstractTeamTaskTool {
    public TeamTaskUpdateTool(TeamManager teamManager) {
        super(teamManager);
    }

    @Override
    public String name() {
        return "TeamTaskUpdate";
    }

    @Override
    public String description() {
        return "Update a shared team task (status, assignee, dependency edges).";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "taskId", new ToolParameterSchema.PropertyDef("string", "Task id", null, null),
                "title", new ToolParameterSchema.PropertyDef("string", "Title", null, null),
                "description", new ToolParameterSchema.PropertyDef("string", "Description", null, null),
                "status", new ToolParameterSchema.PropertyDef("string", "Status", null, null),
                "assignee", new ToolParameterSchema.PropertyDef("string", "Assignee", null, null),
                "addBlocks", new ToolParameterSchema.PropertyDef("array", "Add blocks edges", null, null),
                "addBlockedBy", new ToolParameterSchema.PropertyDef("array", "Add blockedBy edges", null, null),
                "removeBlocks", new ToolParameterSchema.PropertyDef("array", "Remove blocks", null, null),
                "removeBlockedBy", new ToolParameterSchema.PropertyDef("array", "Remove blockedBy", null, null),
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null)),
            List.of("taskId"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            Team team = resolveTeam(params);
            SharedTask t = store(team).update(
                str(params, "taskId"),
                str(params, "title"),
                str(params, "description"),
                str(params, "status"),
                str(params, "assignee"),
                strList(params, "addBlocks"),
                strList(params, "addBlockedBy"),
                strList(params, "removeBlocks"),
                strList(params, "removeBlockedBy"));
            return ToolResult.success("updated", t.toString());
        } catch (Exception e) {
            return ToolResult.error("TEAM_TASK", e.getMessage(), "");
        }
    }
}
