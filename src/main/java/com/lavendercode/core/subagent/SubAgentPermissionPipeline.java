package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SubAgentPermissionPipeline implements PermissionEvaluator {

    private final List<PermissionLayer> layers;
    private final HitlGate hitlGate;
    private final Path projectRoot;

    private SubAgentPermissionPipeline(
        List<PermissionLayer> layers,
        HitlGate hitlGate,
        Path projectRoot) {
        this.layers = layers;
        this.hitlGate = hitlGate;
        this.projectRoot = projectRoot;
    }

    public static SubAgentPermissionPipeline create(
        RuleEngineLayer parentEngine,
        PermissionMode subAgentMode,
        HitlGate hitlGate,
        Path projectRoot,
        String agentName,
        java.util.function.Consumer<List<PermissionRule>> reloadLocalRules) {
        HitlGate wrappedGate = new SubAgentHitlGate(hitlGate, agentName);
        List<PermissionLayer> layers = List.of(
            new BlacklistLayer(),
            new SandboxLayer(),
            new ParentAllowLayer(parentEngine),
            new SubAgentModeLayer(subAgentMode));
        return new SubAgentPermissionPipeline(layers, wrappedGate, projectRoot);
    }

    @Override
    public PermissionOutcome evaluate(ToolCallContext ctx, AtomicBoolean cancelFlag) {
        if (ctx.parseFailed()) {
            return PermissionOutcome.deny(new PermissionDecision.Deny(
                "PARSE",
                "工具参数解析失败",
                "请检查工具调用参数格式"));
        }

        for (PermissionLayer layer : layers) {
            Optional<PermissionDecision> decision = layer.evaluate(ctx);
            if (decision.isEmpty()) {
                continue;
            }
            return switch (decision.get()) {
                case PermissionDecision.Allow allow -> PermissionOutcome.allow();
                case PermissionDecision.Deny deny -> PermissionOutcome.deny(deny);
                case PermissionDecision.Ask ask -> resolveHitl(ctx, ask, cancelFlag);
            };
        }
        return PermissionOutcome.deny(new PermissionDecision.Deny(
            "INTERNAL",
            "权限管道未产出结论",
            ""));
    }

    private PermissionOutcome resolveHitl(
        ToolCallContext ctx,
        PermissionDecision.Ask ask,
        AtomicBoolean cancelFlag) {
        if (cancelFlag.get()) {
            return PermissionOutcome.deny(new PermissionDecision.Deny("USER", "用户取消", ""));
        }
        HitlRequest request = new HitlRequest(
            ctx.friendlyName(), ctx.matchKey(), ask.triggerReason(), 0);
        HitlChoice choice = hitlGate.awaitDecision(request, cancelFlag);
        return switch (choice) {
            case ALLOW_ONCE -> PermissionOutcome.allow();
            case ALLOW_PERMANENT -> PermissionOutcome.allow();
            case DENY -> PermissionOutcome.deny(new PermissionDecision.Deny(
                "USER",
                "用户拒绝本次操作",
                "请调整策略或请求用户授权"));
        };
    }
}
