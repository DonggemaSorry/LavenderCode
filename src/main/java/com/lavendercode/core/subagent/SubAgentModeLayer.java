package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.ModeFallbackLayer;
import com.lavendercode.core.permission.PermissionDecision;
import com.lavendercode.core.permission.PermissionLayer;
import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.permission.ToolCallContext;
import java.util.Optional;

public final class SubAgentModeLayer implements PermissionLayer {

    private final ModeFallbackLayer fallback;

    public SubAgentModeLayer(PermissionMode mode) {
        this.fallback = new ModeFallbackLayer(() -> mode);
    }

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        return fallback.evaluate(ctx);
    }
}
