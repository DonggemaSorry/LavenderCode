package com.lavendercode.core.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RuleEngineLayer implements PermissionLayer {

    private final List<List<PermissionRule>> tiers;

    private RuleEngineLayer(List<List<PermissionRule>> tiers) {
        this.tiers = tiers;
    }

    public static RuleEngineLayer ofRules(List<PermissionRule> rules) {
        return new RuleEngineLayer(List.of(rules));
    }

    public static RuleEngineLayer fromTiers(
        List<PermissionRule> local,
        List<PermissionRule> project,
        List<PermissionRule> user) {
        return new RuleEngineLayer(List.of(local, project, user));
    }

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        for (List<PermissionRule> tier : tiers) {
            Optional<PermissionDecision> decision = evaluateTier(tier, ctx);
            if (decision.isPresent()) {
                return decision;
            }
        }
        return Optional.empty();
    }

    private Optional<PermissionDecision> evaluateTier(List<PermissionRule> rules, ToolCallContext ctx) {
        List<PermissionRule> denyFirst = new ArrayList<>(rules);
        denyFirst.sort((a, b) -> {
            if (a.effect() == b.effect()) {
                return 0;
            }
            return a.effect() == PermissionRule.Effect.DENY ? -1 : 1;
        });
        for (PermissionRule rule : denyFirst) {
            if (rule.matches(ctx)) {
                return Optional.of(toDecision(rule));
            }
        }
        return Optional.empty();
    }

    private PermissionDecision toDecision(PermissionRule rule) {
        return switch (rule.effect()) {
            case ALLOW -> new PermissionDecision.Allow();
            case DENY -> new PermissionDecision.Deny(
                "RULE",
                "规则拒绝: " + rule.toolName() + "(" + rule.pattern() + ")",
                "调整 permissions.yaml 或请求用户授权");
        };
    }
}
