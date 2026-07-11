package com.lavendercode.core.mcp;

import java.util.regex.Pattern;

public final class McpToolNaming {
    private static final Pattern LLM_TOOL_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private McpToolNaming() {}

    public static String registryName(String serverName, String remoteToolName) {
        return "mcp__" + serverName + "__" + remoteToolName;
    }

    public static boolean isValidRegistryName(String name) {
        return LLM_TOOL_NAME.matcher(name).matches();
    }
}
