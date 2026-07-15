package com.lavendercode.core.tool;

import com.lavendercode.core.team.SharedTaskStore;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import com.lavendercode.core.team.TeammateContext;
import java.util.List;
import java.util.Map;

abstract class AbstractTeamTaskTool implements Tool {
    protected final TeamManager teamManager;

    protected AbstractTeamTaskTool(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    protected Team resolveTeam(Map<String, Object> params) {
        String teamName = str(params, "teamName");
        if (teamName == null || teamName.isBlank()) {
            TeammateContext ctx = TeammateContext.get();
            if (ctx != null) {
                teamName = ctx.teamName();
            }
        }
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("缺少 teamName（参数或 TeammateContext）");
        }
        final String key = teamName;
        return teamManager.get(key)
            .orElseThrow(() -> new IllegalArgumentException("团队不存在: " + key));
    }

    protected SharedTaskStore store(Team team) {
        return new SharedTaskStore(team.configDir());
    }

    protected static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    protected static List<String> strList(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(v));
    }
}
