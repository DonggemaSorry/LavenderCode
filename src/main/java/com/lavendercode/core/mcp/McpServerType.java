package com.lavendercode.core.mcp;

public enum McpServerType {
    STDIO,
    HTTP;

    public static McpServerType fromYaml(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "stdio" -> STDIO;
            case "http" -> HTTP;
            default -> null;
        };
    }
}
