package com.lavendercode.chat.terminal;

import com.lavendercode.core.skill.SkillHost;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.function.Predicate;

final class SkillHostImpl implements SkillHost {
    private final NetworkOrchestrator orch;

    SkillHostImpl(NetworkOrchestrator orch) {
        this.orch = orch;
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
}
