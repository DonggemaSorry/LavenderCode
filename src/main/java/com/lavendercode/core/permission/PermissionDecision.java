package com.lavendercode.core.permission;

public sealed interface PermissionDecision {
    record Allow() implements PermissionDecision {}

    record Deny(String source, String reason, String suggestion) implements PermissionDecision {}

    record Ask(String triggerReason) implements PermissionDecision {}
}
