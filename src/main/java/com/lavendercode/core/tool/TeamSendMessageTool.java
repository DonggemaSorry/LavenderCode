package com.lavendercode.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.team.Backend;
import com.lavendercode.core.team.BackendFactory;
import com.lavendercode.core.team.BackendType;
import com.lavendercode.core.team.MailMessage;
import com.lavendercode.core.team.Mailbox;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import com.lavendercode.core.team.TeammateContext;
import com.lavendercode.core.team.TeammateInfo;
import com.lavendercode.core.team.TeammateResumeHandler;
import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamSendMessageTool implements Tool {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TeamManager teamManager;
    private final TeammateResumeHandler resumeHandler;

    public TeamSendMessageTool(TeamManager teamManager) {
        this(teamManager, null);
    }

    public TeamSendMessageTool(TeamManager teamManager, TeammateResumeHandler resumeHandler) {
        this.teamManager = teamManager;
        this.resumeHandler = resumeHandler;
    }

    @Override
    public String name() {
        return "TeamSendMessage";
    }

    @Override
    public String description() {
        return "Send a message to a teammate by name/id, or broadcast with to=\"*\".";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "to", new ToolParameterSchema.PropertyDef("string", "name | agentId | *", null, null),
                "summary", new ToolParameterSchema.PropertyDef("string", "5-10 word summary", null, null),
                "message", new ToolParameterSchema.PropertyDef("string", "Body", null, null),
                "type", new ToolParameterSchema.PropertyDef(
                    "string", "text|shutdown_request|shutdown_response|plan_approval_response", null, null),
                "payload", new ToolParameterSchema.PropertyDef("object", "Structured payload", null, null),
                "teamName", new ToolParameterSchema.PropertyDef("string", "Team name", null, null)),
            List.of("to"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (teamManager == null) {
            return ToolResult.error("NO_TEAM_MANAGER", "TeamManager 未配置", "");
        }
        try {
            TeammateContext ctx = TeammateContext.get();
            String rawTeamName = str(params, "teamName");
            final String teamName;
            if (rawTeamName == null || rawTeamName.isBlank()) {
                if (ctx == null) {
                    return ToolResult.error("VALIDATION", "缺少 team 上下文或 teamName", "");
                }
                teamName = ctx.teamName();
            } else {
                teamName = rawTeamName;
            }
            Team team = teamManager.get(teamName)
                .orElseThrow(() -> new IllegalArgumentException("团队不存在: " + teamName));

            String from = ctx != null ? ctx.memberName() : "lead";
            boolean isLead = ctx == null || ctx.isLead() || "lead".equals(from);
            String type = str(params, "type");
            if (type == null || type.isBlank()) {
                type = "text";
            }
            if ("plan_approval_response".equals(type) && !isLead) {
                return ToolResult.error("FORBIDDEN", "仅 Lead 可发送 plan_approval_response", "");
            }
            String to = str(params, "to");
            if (to == null || to.isBlank()) {
                return ToolResult.error("VALIDATION", "to 必填", "");
            }
            if ("text".equals(type)) {
                String summary = str(params, "summary");
                if (summary == null || summary.isBlank()) {
                    return ToolResult.error("VALIDATION", "纯文本消息必须提供 summary", "");
                }
            }

            Mailbox mailbox = new Mailbox(team.configDir());
            List<String> delivered = new ArrayList<>();
            long ts = System.currentTimeMillis();
            String summary = str(params, "summary");
            String body = str(params, "message");
            Object payload = params.get("payload");

            if ("*".equals(to)) {
                for (TeammateInfo m : team.membersView()) {
                    if (m.name().equals(from)) {
                        continue;
                    }
                    TeammateInfo target = ensureAwake(team, m, body);
                    MailMessage copy = new MailMessage(
                        from, target.agentId(), type, summary, body, payload, ts, false);
                    mailbox.write(target.agentId(), copy);
                    delivered.add(target.agentId());
                }
            } else {
                TeammateInfo member = resolveMember(team, to);
                if ("shutdown_response".equals(type) && !"lead".equals(member.name())) {
                    return ToolResult.error("VALIDATION", "shutdown_response 只能发给 Lead", "");
                }
                TeammateInfo target = ensureAwake(team, member, body);
                MailMessage copy = new MailMessage(
                    from, target.agentId(), type, summary, body, payload, ts, false);
                mailbox.write(target.agentId(), copy);
                delivered.add(target.agentId());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deliveredTo", delivered);
            result.put("timestamp", ts);
            return ToolResult.success("delivered", JSON.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.error("TEAM_SEND", e.getMessage(), "");
        }
    }

    private TeammateInfo resolveMember(Team team, String to) {
        // 优先本 Team 内按名/id 解析，避免全局 registry 跨 Team 误匹配
        return team.findMember(to)
            .or(() -> team.membersView().stream()
                .filter(m -> m.agentId().equals(to)).findFirst())
            .or(() -> teamManager.registry().resolve(to)
                .flatMap(id -> team.membersView().stream()
                    .filter(m -> m.agentId().equals(id))
                    .findFirst()))
            .orElseThrow(() -> new IllegalArgumentException("无法解析收件人: " + to));
    }

    /** 若 in-process 队员已终止则先 resume，再返回最新 roster 成员（含新 agentId）。 */
    private TeammateInfo ensureAwake(Team team, TeammateInfo member, String message) throws Exception {
        if ("lead".equals(member.name())) {
            return member;
        }
        if (member.backendType() == BackendType.IN_PROCESS) {
            TaskManager tm = teamManager.taskManager();
            if (tm != null) {
                BackgroundTask t = tm.get(member.agentId());
                if ((t == null || t.isTerminated()) && resumeHandler != null) {
                    resumeHandler.resumeIfStopped(team, member, message == null ? "" : message);
                    return team.findMember(member.name()).orElse(member);
                }
            }
            return member;
        }
        if (member.paneId() != null && !member.paneId().isBlank()) {
            try {
                Backend backend = BackendFactory.create(member.backendType(), teamManager.taskManager());
                backend.wake(member.paneId(), member.agentId());
            } catch (Exception e) {
                System.err.println("WARN: wake teammate failed: " + e.getMessage());
            }
        }
        return member;
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
