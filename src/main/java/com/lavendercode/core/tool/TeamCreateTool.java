package com.lavendercode.core.tool;

import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.List;
import java.util.Map;

public final class TeamCreateTool implements Tool {
    private final TeamManager teamManager;

    public TeamCreateTool(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public String name() {
        return "TeamCreate";
    }

    @Override
    public String description() {
        return "Create a long-lived agent team (Lead becomes first member).";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null),
                "description", new ToolParameterSchema.PropertyDef("string", "Optional description", null, null),
                "agentType", new ToolParameterSchema.PropertyDef("string", "Reserved", null, null)),
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
        try {
            Team t = teamManager.create(teamName, str(params, "agentType"));
            String desc = str(params, "description");
            if (desc != null && !desc.isBlank()) {
                t.setDescription(desc);
                t.saveAtomic();
            }
            String json = "{\"teamName\":\"" + t.sanitizedName()
                + "\",\"backend\":\"" + t.backend().wireValue()
                + "\",\"configPath\":\"" + t.configPath().toString().replace("\\", "\\\\") + "\"}";
            return ToolResult.success("team created", json);
        } catch (Exception e) {
            return ToolResult.error("TEAM_CREATE", e.getMessage(), "");
        }
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
