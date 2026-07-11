package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class McpSessionManager {
    private final McpJsonMapper jsonMapper;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> registryToRemote = new ConcurrentHashMap<>();

    public McpSessionManager() {
        this(McpJsonMapperFactory.get());
    }

    McpSessionManager(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void connectAndRegisterTools(McpServerConfig config) {
        var transport = McpTransportFactory.createTransport(config, jsonMapper);
        McpSyncClient client = McpTransportFactory.createClient(transport);
        client.initialize();
        McpSchema.ListToolsResult tools = client.listTools();
        Map<String, String> remoteByRegistry = new HashMap<>();
        for (McpSchema.Tool remoteTool : tools.tools()) {
            registerRemoteTool(config.name(), remoteTool, client, remoteByRegistry);
        }
        sessions.put(config.name(), new McpSession(config.name(), client, Map.copyOf(remoteByRegistry)));
    }

    private void registerRemoteTool(
            String serverName,
            McpSchema.Tool remoteTool,
            McpSyncClient client,
            Map<String, String> remoteByRegistry) {
        String registryName = McpToolNaming.registryName(serverName, remoteTool.name());
        if (!McpToolNaming.isValidRegistryName(registryName)) {
            System.err.println("WARN: MCP tool skipped due to invalid registry name: " + registryName);
            return;
        }
        if (ToolRegistry.has(registryName)) {
            System.err.println("WARN: MCP tool name conflict, replacing: " + registryName);
            ToolRegistry.unregister(registryName);
        }
        boolean readOnly = remoteTool.annotations() != null
            && Boolean.TRUE.equals(remoteTool.annotations().readOnlyHint());
        String description = remoteTool.description();
        if (description == null || description.isBlank()) {
            description = "MCP tool from server " + serverName;
        }
        Tool adapter = new McpToolAdapter(registryName, description, remoteTool, readOnly, this);
        ToolRegistry.register(adapter);
        remoteByRegistry.put(registryName, remoteTool.name());
        registryToRemote.put(registryName, remoteTool.name());
    }

    public McpSchema.CallToolResult callTool(String registryName, Map<String, Object> params) {
        McpSession session = findSessionForRegistry(registryName);
        String remoteName = session.remoteToolByRegistry().get(registryName);
        if (remoteName == null) {
            throw new IllegalStateException("Unknown MCP tool: " + registryName);
        }
        return session.client().callTool(new McpSchema.CallToolRequest(remoteName, params));
    }

    public void closeAll(Duration budget) {
        if (sessions.isEmpty()) {
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = sessions.values().stream()
                .map(session -> executor.submit(() -> closeSession(session)))
                .toList();
            long deadline = System.nanoTime() + budget.toNanos();
            for (Future<?> future : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(remaining, TimeUnit.NANOSECONDS);
                } catch (Exception ignored) {
                    future.cancel(true);
                }
            }
        } finally {
            sessions.clear();
            registryToRemote.clear();
        }
    }

    private void closeSession(McpSession session) {
        try {
            session.client().closeGracefully();
        } catch (Exception ignored) {
            try {
                session.client().close();
            } catch (Exception ignoredAgain) {
                // best effort
            }
        }
    }

    private McpSession findSessionForRegistry(String registryName) {
        String prefix = registryName.substring("mcp__".length());
        int split = prefix.indexOf("__");
        if (split <= 0) {
            throw new IllegalStateException("Invalid MCP registry name: " + registryName);
        }
        String serverName = prefix.substring(0, split);
        McpSession session = sessions.get(serverName);
        if (session == null) {
            throw new IllegalStateException("No MCP session for tool: " + registryName);
        }
        return session;
    }

    int sessionCount() {
        return sessions.size();
    }
}
