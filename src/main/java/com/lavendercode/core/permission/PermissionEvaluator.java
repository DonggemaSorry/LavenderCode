package com.lavendercode.core.permission;

import java.util.concurrent.atomic.AtomicBoolean;

public interface PermissionEvaluator {
    PermissionOutcome evaluate(ToolCallContext ctx, AtomicBoolean cancelFlag);
}
