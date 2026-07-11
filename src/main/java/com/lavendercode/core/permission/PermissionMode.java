package com.lavendercode.core.permission;

public enum PermissionMode {
    DEFAULT,
    ACCEPT_EDITS,
    PLAN,
    BYPASS_PERMISSIONS;

    public String label() {
        return switch (this) {
            case DEFAULT -> "default";
            case ACCEPT_EDITS -> "acceptEdits";
            case PLAN -> "plan";
            case BYPASS_PERMISSIONS -> "bypassPermissions";
        };
    }

    public static PermissionMode fromYaml(String s) {
        if (s == null) {
            return DEFAULT;
        }
        return switch (s.trim()) {
            case "acceptEdits" -> ACCEPT_EDITS;
            case "plan" -> PLAN;
            case "bypassPermissions" -> BYPASS_PERMISSIONS;
            default -> DEFAULT;
        };
    }
}
