package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.skill.SkillForkHost;
import com.lavendercode.core.tool.ToolRegistry;
import java.util.List;
import java.util.function.Predicate;

final class SkillForkHostImpl implements SkillForkHost {
    private final NetworkOrchestrator orch;

    SkillForkHostImpl(NetworkOrchestrator orch) {
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

    @Override
    public String runSubAgent(String body, List<Message> seed,
                               List<String> allowedTools, String model) {
        // Phase 2 基础实现——使用当前 provider 跑一轮对话
        // 完整实现需要创建子 Agent 循环，此处先返回占位文本
        // 后续迭代中完善子 Agent 对话能力
        orch.safePut(new RenderEvent.AddSystemMessage("[fork 模式正在执行子 Agent...]"));
        return "[Skill fork 结果]: " + body.substring(0, Math.min(100, body.length()));
    }

    @Override
    public List<Message> snapshotParentMessages() {
        if (orch.sessionManager != null) {
            return orch.sessionManager.getHistory();
        }
        return List.of();
    }
}
