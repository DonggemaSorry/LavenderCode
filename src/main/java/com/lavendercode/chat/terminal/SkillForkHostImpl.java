package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.skill.SkillForkHost;
import com.lavendercode.core.subagent.AgentCatalog;
import com.lavendercode.core.subagent.AgentDefinition;
import com.lavendercode.core.subagent.SubAgentCallContext;
import com.lavendercode.core.subagent.SubAgentLauncher;
import com.lavendercode.core.subagent.SubAgentServices;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

final class SkillForkHostImpl implements SkillForkHost {
    private final NetworkOrchestrator orch;
    private final SubAgentServices subAgentServices;

    SkillForkHostImpl(NetworkOrchestrator orch, SubAgentServices subAgentServices) {
        this.orch = orch;
        this.subAgentServices = subAgentServices;
    }

    @Override
    public void activateSkill(String name, String body) {
        orch.safePut(new RenderEvent.AddSystemMessage(
            "[已激活技能: " + name + "]"));
    }

    @Override
    public void setToolFilter(Predicate<String> filter) {
        ToolRegistry.setFilter(filter);
    }

    @Override
    public boolean hasTool(String name) {
        return ToolRegistry.has(name);
    }

    @Override
    public String runSubAgent(String body, List<Message> seed,
                               List<String> allowedTools, String model) {
        orch.safePut(new RenderEvent.AddSystemMessage("[fork 模式正在执行子 Agent...]"));
        AgentDefinition temp = new AgentDefinition(
            "skill-fork-temp",
            "skill fork",
            allowedTools,
            List.of(),
            model != null ? model : "inherit",
            AgentDefinition.DEFAULT_MAX_TURNS,
            PermissionMode.DEFAULT,
            false,
            body,
            AgentCatalog.Source.BUILTIN);
        return SubAgentCallContext.run(SubAgentCallContext.Kind.FORK, () ->
            SubAgentLauncher.runWithSeed(
                subAgentServices, temp, seed, body, new AtomicBoolean(false)));
    }

    @Override
    public List<Message> snapshotParentMessages() {
        if (orch.sessionManager != null) {
            return orch.sessionManager.getHistory();
        }
        return List.of();
    }
}
