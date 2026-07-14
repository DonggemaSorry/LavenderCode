package com.lavendercode.core.permission;

public record PermissionRule(String toolName, PatternMatcher patternMatcher, Effect effect) {

    public enum Effect {
        ALLOW,
        DENY
    }

    public static PermissionRule parse(String ruleText, Effect effect) {
        String trimmed = ruleText.trim();
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("Invalid rule format: " + ruleText);
        }
        String toolName = trimmed.substring(0, open).trim();
        String pattern = trimmed.substring(open + 1, close).trim();
        return new PermissionRule(toolName, PatternMatcher.parse(pattern), effect);
    }

    public boolean matches(ToolCallContext ctx) {
        if (!GlobMatcher.matches(ctx.friendlyName(), toolName, false)) {
            return false;
        }
        return patternMatcher.matches(ctx.matchKey());
    }
}
