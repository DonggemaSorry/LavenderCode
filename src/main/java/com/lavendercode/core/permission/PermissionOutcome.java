package com.lavendercode.core.permission;

public record PermissionOutcome(boolean allowed, PermissionDecision.Deny deny) {
    public static PermissionOutcome allow() {
        return new PermissionOutcome(true, null);
    }

    public static PermissionOutcome deny(PermissionDecision.Deny d) {
        return new PermissionOutcome(false, d);
    }

    public boolean denied() {
        return !allowed;
    }
}
