package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionRestorer;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.subagent.AgentDefinition;
import com.lavendercode.core.subagent.SubAgentLauncher;
import com.lavendercode.core.subagent.SubAgentServices;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import com.lavendercode.core.team.TeammateContext;
import com.lavendercode.core.team.TeammateInfo;
import com.lavendercode.core.team.TeammateResumeHandler;
import com.lavendercode.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultTeammateResumeHandler implements TeammateResumeHandler {
    private final TeamManager teamManager;
    private final SubAgentServices services;

    public DefaultTeammateResumeHandler(TeamManager teamManager, SubAgentServices services) {
        this.teamManager = teamManager;
        this.services = services;
    }

    @Override
    public void resumeIfStopped(Team team, TeammateInfo member, String message) throws Exception {
        Path jsonl = member.sessionDir() == null
            ? null
            : member.sessionDir().resolve("conversation.jsonl");
        List<Message> hist = (jsonl != null && java.nio.file.Files.exists(jsonl))
            ? SessionRestorer.parseMessages(jsonl)
            : List.of();
        team.setMemberActive(member.name(), true);
        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicReference<List<Message>> out = new AtomicReference<>();
        AgentDefinition def = AgentDefinition.forkBase(message);
        ToolContext ctx = member.worktreePath() == null
            ? ToolContext.empty()
            : ToolContext.empty().withCwd(member.worktreePath());
        Callable<String> work = () -> {
            TeammateContext.set(new TeammateContext(
                team.sanitizedName(), member.name(), member.agentId(), false));
            try {
                return SubAgentLauncher.buildWork(
                    services, def, message, true, true, hist, cancel, out, ctx).call();
            } finally {
                try {
                    team.setMemberActive(member.name(), false);
                } finally {
                    TeammateContext.clear();
                }
            }
        };
        String newId = teamManager.taskManager().launch(work, member.name(), hist, out);
        teamManager.registry().register(member.name(), newId);
        // update agentId in roster
        team.removeMember(member.name());
        team.addMember(new TeammateInfo(
            member.name(), newId, member.agentType(), member.model(),
            member.worktreePath(), member.branch(), member.backendType(),
            member.paneId(), true, member.planModeRequired(), member.sessionDir()));
    }
}
