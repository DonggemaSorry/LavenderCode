package com.lavendercode.core.permission;

public record PermissionRule(String toolName, String pattern, Effect effect) {

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
        return new PermissionRule(toolName, pattern, effect);
    }

    public boolean matches(ToolCallContext ctx) {
        if (!toolName.equals(ctx.friendlyName())) {
            return false;
        }
        boolean pathMode = ctx.category() != ToolCategory.COMMAND;
        return GlobMatcher.matches(ctx.matchKey(), pattern, pathMode);
    }
}
