package com.lavendercode.core.permission;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PermissionPipeline implements PermissionEvaluator {

    private final List<PermissionLayer> layers;
    private final HitlGate hitlGate;
    private final Path projectRoot;
    private final AtomicReference<RuleEngineLayer> ruleEngineRef;
    private final Consumer<List<PermissionRule>> reloadLocalRules;

    private PermissionPipeline(
        List<PermissionLayer> layers,
        HitlGate hitlGate,
        Path projectRoot,
        AtomicReference<RuleEngineLayer> ruleEngineRef,
        Consumer<List<PermissionRule>> reloadLocalRules) {
        this.layers = layers;
        this.hitlGate = hitlGate;
        this.projectRoot = projectRoot;
        this.ruleEngineRef = ruleEngineRef;
        this.reloadLocalRules = reloadLocalRules;
    }

    public static PermissionPipeline create(
        PermissionConfig config,
        Supplier<PermissionMode> modeSupplier,
        HitlGate hitlGate,
        Path projectRoot,
        Consumer<List<PermissionRule>> reloadLocalRules) {
        AtomicReference<RuleEngineLayer> ruleEngineRef = new AtomicReference<>(
            RuleEngineLayer.fromTiers(config.localRules(), config.projectRules(), config.userRules()));
        PermissionLayer ruleLayer = ctx -> ruleEngineRef.get().evaluate(ctx);
        List<PermissionLayer> layers = List.of(
            new BlacklistLayer(),
            new SandboxLayer(),
            ruleLayer,
            new ModeFallbackLayer(modeSupplier));
        return new PermissionPipeline(layers, hitlGate, projectRoot, ruleEngineRef, reloadLocalRules);
    }

    public RuleEngineLayer ruleEngineLayer() {
        return ruleEngineRef.get();
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
        HitlRequest request = new HitlRequest(ctx.friendlyName(), ctx.matchKey(), ask.triggerReason(), 0);
        HitlChoice choice = hitlGate.awaitDecision(request, cancelFlag);
        return switch (choice) {
            case ALLOW_ONCE -> PermissionOutcome.allow();
            case ALLOW_PERMANENT -> {
                persistExactAllowRule(ctx);
                yield PermissionOutcome.allow();
            }
            case DENY -> PermissionOutcome.deny(new PermissionDecision.Deny(
                "USER",
                "用户拒绝本次操作",
                "请调整策略或请求用户授权"));
        };
    }

    private void persistExactAllowRule(ToolCallContext ctx) {
        String ruleText = ctx.friendlyName() + "(" + ctx.matchKey() + ")";
        Path localPath = projectRoot.resolve(".lavendercode/permissions.local.yaml");
        try {
            LocalPermissionWriter.appendRule(localPath, ruleText, PermissionRule.Effect.ALLOW);
            PermissionConfig reloaded = PermissionConfigLoader.load(
                projectRoot,
                Path.of(System.getProperty("user.home")).resolve(".lavendercode"));
            ruleEngineRef.set(RuleEngineLayer.fromTiers(
                reloaded.localRules(), reloaded.projectRules(), reloaded.userRules()));
            reloadLocalRules.accept(reloaded.localRules());
        } catch (Exception e) {
            System.err.println("WARN: failed to persist allow rule: " + e.getMessage());
        }
    }
}
