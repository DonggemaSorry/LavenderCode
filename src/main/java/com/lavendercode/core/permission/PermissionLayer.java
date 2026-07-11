package com.lavendercode.core.permission;

import java.util.Optional;

public interface PermissionLayer {
    /** @return empty = 本层未决，继续下一层；non-empty = 短路 */
    Optional<PermissionDecision> evaluate(ToolCallContext ctx);
}
