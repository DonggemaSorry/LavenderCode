package com.lavendercode.core.permission;

import java.util.List;

public record PermissionConfig(
    List<PermissionRule> localRules,
    List<PermissionRule> projectRules,
    List<PermissionRule> userRules,
    PermissionMode defaultMode) {

    public PermissionConfig(List<PermissionRule> rules, PermissionMode defaultMode) {
        this(rules, List.of(), List.of(), defaultMode);
    }

    public static PermissionConfig empty() {
        return new PermissionConfig(List.of(), List.of(), List.of(), PermissionMode.DEFAULT);
    }

    public List<PermissionRule> rules() {
        if (!localRules.isEmpty()) {
            return localRules;
        }
        if (!projectRules.isEmpty()) {
            return projectRules;
        }
        return userRules;
    }
}
