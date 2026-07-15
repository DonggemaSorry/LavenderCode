package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.PermissionDecision;
import com.lavendercode.core.permission.PermissionLayer;
import com.lavendercode.core.permission.RuleEngineLayer;
import com.lavendercode.core.permission.ToolCallContext;
import java.util.Optional;

public final class ParentAllowLayer implements PermissionLayer {

    private final RuleEngineLayer parentEngine;

    public ParentAllowLayer(RuleEngineLayer parentEngine) {
        this.parentEngine = parentEngine;
    }

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        Optional<PermissionDecision> decision = parentEngine.evaluate(ctx);
        if (decision.isPresent() && decision.get() instanceof PermissionDecision.Allow) {
            return decision;
        }
        return Optional.empty();
    }
}
