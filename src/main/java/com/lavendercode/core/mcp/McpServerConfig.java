package com.lavendercode.core.mcp;

import java.util.List;
import java.util.Map;

public record McpServerConfig(
        String name,
        McpServerType type,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        Map<String, String> headers) {

    public static McpServerConfig stdio(
            String name, String command, List<String> args, Map<String, String> env) {
        return new McpServerConfig(name, McpServerType.STDIO, command, args, env, null, Map.of());
    }

    public static McpServerConfig http(String name, String url, Map<String, String> headers) {
        return new McpServerConfig(name, McpServerType.HTTP, null, List.of(), Map.of(), url, headers);
    }
}
