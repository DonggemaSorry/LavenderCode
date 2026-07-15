package com.lavendercode.core.tool;

import com.lavendercode.core.team.SharedTask;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TeamTaskListTool extends AbstractTeamTaskTool {
    public TeamTaskListTool(TeamManager teamManager) {
        super(teamManager);
    }

    @Override
    public String name() {
        return "TeamTaskList";
    }

    @Override
    public String description() {
        return "List shared team tasks; optional status filter; includes isReady.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "status", new ToolParameterSchema.PropertyDef("string", "pending|in_progress|completed|blocked", null, null),
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null)),
            List.of());
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            Team team = resolveTeam(params);
            List<SharedTask> tasks = store(team).list(str(params, "status"));
            String body = tasks.stream()
                .map(t -> t.id() + " [" + t.status() + "] ready=" + t.isReady()
                    + " title=" + t.title()
                    + " blockedBy=" + t.blockedBy()
                    + " blocks=" + t.blocks())
                .collect(Collectors.joining("\n"));
            if (body.isBlank()) {
                body = "(no tasks)";
            }
            return ToolResult.success("tasks", body);
        } catch (Exception e) {
            return ToolResult.error("TEAM_TASK", e.getMessage(), "");
        }
    }
}
