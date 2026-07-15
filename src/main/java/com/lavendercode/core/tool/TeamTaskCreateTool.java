package com.lavendercode.core.tool;

import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;

public final class TeamTaskCreateTool extends AbstractTeamTaskTool {
    public TeamTaskCreateTool(TeamManager teamManager) {
        super(teamManager);
    }

    @Override
    public String name() {
        return "TeamTaskCreate";
    }

    @Override
    public String description() {
        return "Create a shared team task (optional assignee and blockedBy).";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "title", new ToolParameterSchema.PropertyDef("string", "Title", null, null),
                "description", new ToolParameterSchema.PropertyDef("string", "Description", null, null),
                "assignee", new ToolParameterSchema.PropertyDef("string", "Member name", null, null),
                "blockedBy", new ToolParameterSchema.PropertyDef("array", "Blocking task ids", null, null),
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null)),
            List.of("title"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            Team team = resolveTeam(params);
            String id = store(team).create(
                str(params, "title"),
                str(params, "description"),
                str(params, "assignee"),
                strList(params, "blockedBy") == null ? List.of() : strList(params, "blockedBy"));
            return ToolResult.success("task created", "{\"taskId\":\"" + id + "\"}");
        } catch (Exception e) {
            return ToolResult.error("TEAM_TASK", e.getMessage(), "");
        }
    }
}
