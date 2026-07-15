package com.lavendercode.core.tool;

import com.lavendercode.core.team.SharedTask;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;

public final class TeamTaskGetTool extends AbstractTeamTaskTool {
    public TeamTaskGetTool(TeamManager teamManager) {
        super(teamManager);
    }

    @Override
    public String name() {
        return "TeamTaskGet";
    }

    @Override
    public String description() {
        return "Get a shared team task by id.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "taskId", new ToolParameterSchema.PropertyDef("string", "Task id", null, null),
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null)),
            List.of("taskId"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            Team team = resolveTeam(params);
            SharedTask t = store(team).get(str(params, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
            return ToolResult.success("task", t.toString());
        } catch (Exception e) {
            return ToolResult.error("TEAM_TASK", e.getMessage(), "");
        }
    }
}
