package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionRestorer;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.subagent.AgentDefinition;
import com.lavendercode.core.subagent.SubAgentLauncher;
import com.lavendercode.core.subagent.SubAgentServices;
import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.team.IncomingMailHook;
import com.lavendercode.core.team.Mailbox;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import com.lavendercode.core.team.TeammateContext;
import com.lavendercode.core.team.TeammateInfo;
import com.lavendercode.core.team.TeammateResumeHandler;
import com.lavendercode.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultTeammateResumeHandler implements TeammateResumeHandler {
    private final TeamManager teamManager;
    private final SubAgentServices services;
    private final ConcurrentHashMap<String, Object> resumeLocks = new ConcurrentHashMap<>();

    public DefaultTeammateResumeHandler(TeamManager teamManager, SubAgentServices services) {
        this.teamManager = teamManager;
        this.services = services;
    }

    @Override
    public void resumeIfStopped(Team team, TeammateInfo member, String message) throws Exception {
        String lockKey = team.sanitizedName() + "/" + member.name();
        Object monitor = resumeLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (monitor) {
            TeammateInfo current = team.findMember(member.name()).orElse(member);
            TaskManager tm = teamManager.taskManager();
            if (tm == null) {
                throw new IllegalStateException("TaskManager 未配置，无法 resume");
            }
            BackgroundTask existing = tm.get(current.agentId());
            if (existing != null && !existing.isTerminated()) {
                return;
            }

            Path jsonl = current.sessionDir() == null
                ? null
                : current.sessionDir().resolve("conversation.jsonl");
            List<Message> hist = (jsonl != null && java.nio.file.Files.exists(jsonl))
                ? SessionRestorer.parseMessages(jsonl)
                : List.of();
            team.setMemberActive(current.name(), true);
            AtomicBoolean cancel = new AtomicBoolean(false);
            AtomicReference<List<Message>> out = new AtomicReference<>();
            AgentDefinition def = AgentDefinition.forkBase(message);
            ToolContext ctx = current.worktreePath() == null
                ? ToolContext.empty()
                : ToolContext.empty().withCwd(current.worktreePath());
            AtomicReference<String> agentIdRef = new AtomicReference<>();
            CountDownLatch idReady = new CountDownLatch(1);
            String oldId = current.agentId();
            Callable<String> work = () -> {
                idReady.await(10, TimeUnit.SECONDS);
                String aid = agentIdRef.get();
                if (aid == null) {
                    aid = "unknown";
                }
                TeammateContext.set(new TeammateContext(
                    team.sanitizedName(), current.name(), aid, false));
                IncomingMailHook.set(IncomingMailHook.forTeammate(team, aid));
                try {
                    return SubAgentLauncher.buildWork(
                        services, def, message, true, true, hist, cancel, out, ctx).call();
                } finally {
                    try {
                        team.setMemberActive(current.name(), false);
                    } finally {
                        IncomingMailHook.clear();
                        TeammateContext.clear();
                    }
                }
            };
            String newId = tm.launch(work, current.name(), hist, out, cancel);
            agentIdRef.set(newId);
            idReady.countDown();

            new Mailbox(team.configDir()).migrateUnread(oldId, newId);
            teamManager.registry().register(current.name(), newId);
            team.removeMember(current.name());
            team.addMember(new TeammateInfo(
                current.name(), newId, current.agentType(), current.model(),
                current.worktreePath(), current.branch(), current.backendType(),
                current.paneId(), true, current.planModeRequired(), current.sessionDir()));
        }
    }
}
