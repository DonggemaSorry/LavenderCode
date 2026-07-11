package com.lavendercode.core.mcp;

import java.time.Duration;

public final class McpConstants {
    public static final Duration CONNECT_AND_LIST_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration SHUTDOWN_BUDGET = Duration.ofSeconds(5);

    private McpConstants() {}
}
