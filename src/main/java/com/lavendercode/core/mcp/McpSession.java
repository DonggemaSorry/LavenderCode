package com.lavendercode.core.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Map;

public record McpSession(String serverName, McpSyncClient client, Map<String, String> remoteToolByRegistry) {}
