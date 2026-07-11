package com.lavendercode.core.mcp;

public final class McpShutdownHook {
    private static volatile McpSessionManager manager;

    private McpShutdownHook() {}

    public static void register(McpSessionManager sessionManager) {
        manager = sessionManager;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll(sessionManager), "mcp-shutdown"));
    }

    public static void closeAll(McpSessionManager sessionManager) {
        if (sessionManager == null) {
            return;
        }
        sessionManager.closeAll(McpConstants.SHUTDOWN_BUDGET);
    }
}
