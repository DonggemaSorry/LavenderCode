package com.lavendercode.core.hook;

public record HookInterceptResult(boolean blocked, String hookName, String reason) {
    public static HookInterceptResult blocked(String hookName, String reason) {
        return new HookInterceptResult(true, hookName, reason);
    }
    public static HookInterceptResult allowed() {
        return new HookInterceptResult(false, null, null);
    }
}
