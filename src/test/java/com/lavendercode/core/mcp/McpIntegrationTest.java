package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolRegistry;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class McpIntegrationTest {
    @TempDir
    Path projectRoot;

    @TempDir
    Path userConfigDir;

    @BeforeEach
    void setUp() {
        ToolRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void bootstrapConnectsGoodServerWhileSkippingBadServer() throws Exception {
        Files.createDirectories(userConfigDir);
        String javaBin = yamlPath(McpSessionManagerTest.javaCommand());
        String classpath = yamlPath(System.getProperty("java.class.path"));
        Files.writeString(
            userConfigDir.resolve("config.yaml"),
            """
            mcp_servers:
              bad:
                type: stdio
                command: definitely-not-a-real-command-xyz
              good:
                type: stdio
                command: "%s"
                args:
                  - "-cp"
                  - "%s"
                  - "com.lavendercode.core.mcp.MockStdioMcpServer"
            """
                .formatted(javaBin, classpath));
        McpSessionManager manager = new McpSessionManager();
        try {
            McpBootstrap.discoverAndRegister(projectRoot, userConfigDir, manager);
            assertThat(ToolRegistry.has("mcp__good__echo")).isTrue();
            assertThat(manager.sessionCount()).isEqualTo(1);
        } finally {
            McpShutdownHook.closeAll(manager);
        }
    }

    @Test
    void adapterReturnsErrorWhenCallFails() throws Exception {
        McpServerConfig config = mockServerConfig(false);
        McpSessionManager manager = new McpSessionManager();
        try {
            manager.connectAndRegisterTools(config);
            Tool tool = ToolRegistry.get("mcp__mock__echo");
            assertThat(tool).isNotNull();
            manager.closeAll(Duration.ofSeconds(1));
            ToolResult result = tool.execute(Map.of("message", "hi"));
            assertThat(result.success()).isFalse();
            assertThat(result.errorCategory()).isEqualTo("MCP_ERROR");
        } finally {
            manager.closeAll(McpConstants.SHUTDOWN_BUDGET);
        }
    }

    @Test
    void closeAllCompletesWithinShutdownBudget() throws Exception {
        McpServerConfig config = mockServerConfig(false);
        McpSessionManager manager = new McpSessionManager();
        manager.connectAndRegisterTools(config);
        long start = System.nanoTime();
        McpShutdownHook.closeAll(manager);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(McpConstants.SHUTDOWN_BUDGET.toMillis());
        assertThat(manager.sessionCount()).isZero();
    }

    private McpServerConfig mockServerConfig(boolean readOnly) {
        Path javaBin = McpSessionManagerTest.javaCommand();
        String classpath = System.getProperty("java.class.path");
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("-cp");
        args.add(classpath);
        args.add("com.lavendercode.core.mcp.MockStdioMcpServer");
        if (readOnly) {
            args.add("readonly");
        }
        return McpServerConfig.stdio("mock", javaBin.toString(), args, java.util.Map.of());
    }

    private static String yamlPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private static String yamlPath(String path) {
        return path.replace("\\", "/");
    }
}
