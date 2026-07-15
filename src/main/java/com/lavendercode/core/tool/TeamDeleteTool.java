package com.lavendercode.core.tool;

import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;

public final class TeamDeleteTool implements Tool {
    private final TeamManager teamManager;

    public TeamDeleteTool(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public String name() {
        return "TeamDelete";
    }

    @Override
    public String description() {
        return "Delete a team after members are idle (or force=true).";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null),
                "force", new ToolParameterSchema.PropertyDef("boolean", "Force delete", null, null)),
            List.of("teamName"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (teamManager == null) {
            return ToolResult.error("NO_TEAM_MANAGER", "TeamManager 未配置", "");
        }
        String teamName = str(params, "teamName");
        if (teamName == null || teamName.isBlank()) {
            return ToolResult.error("VALIDATION", "teamName 必填", "");
        }
        boolean force = Boolean.TRUE.equals(params.get("force"))
            || "true".equalsIgnoreCase(str(params, "force"));
        try {
            teamManager.delete(teamName, force);
            return ToolResult.success("team deleted", "已删除团队: " + teamName);
        } catch (Exception e) {
            return ToolResult.error("TEAM_DELETE", e.getMessage(), "");
        }
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
