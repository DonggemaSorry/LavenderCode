package com.lavendercode.core.mcp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class McpBootstrap {
    private McpBootstrap() {}

    public static void discoverAndRegister(Path projectRoot, McpSessionManager manager) {
        discoverAndRegister(projectRoot, Path.of(System.getProperty("user.home")).resolve(".LavenderCode"), manager);
    }

    public static void discoverAndRegister(Path projectRoot, Path userConfigDir, McpSessionManager manager) {
        List<McpServerConfig> servers = McpConfigLoader.load(projectRoot, userConfigDir);
        if (servers.isEmpty()) {
            return;
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (McpServerConfig cfg : servers) {
                futures.add(executor.submit(() -> discoverOne(cfg, manager)));
            }
            for (int i = 0; i < futures.size(); i++) {
                Future<?> future = futures.get(i);
                McpServerConfig cfg = servers.get(i);
                try {
                    future.get(McpConstants.CONNECT_AND_LIST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    System.err.println("WARN: MCP server '" + cfg.name() + "' startup timed out after 30s");
                } catch (ExecutionException e) {
                    System.err.println(
                        "WARN: MCP server '" + cfg.name() + "' failed: "
                            + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    System.err.println("WARN: MCP server '" + cfg.name() + "' discovery interrupted");
                }
            }
        }
    }

    private static void discoverOne(McpServerConfig config, McpSessionManager manager) {
        try {
            manager.connectAndRegisterTools(config);
            System.err.println(
                "INFO: MCP server '" + config.name() + "' connected");
        } catch (Exception e) {
            System.err.println("WARN: MCP server '" + config.name() + "' failed: " + e.getMessage());
        }
    }
}
