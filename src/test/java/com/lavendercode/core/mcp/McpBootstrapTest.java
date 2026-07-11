package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class McpBootstrapTest {
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
    void registersToolsFromConfiguredMockServer() throws Exception {
        writeMockConfig("mock");
        McpSessionManager manager = new McpSessionManager();
        try {
            McpBootstrap.discoverAndRegister(projectRoot, userConfigDir, manager);
            assertThat(ToolRegistry.has("mcp__mock__echo")).isTrue();
        } finally {
            manager.closeAll(McpConstants.SHUTDOWN_BUDGET);
        }
    }

    @Test
    void skipsBrokenServerWithoutBlockingGoodServer() throws Exception {
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
        } finally {
            manager.closeAll(McpConstants.SHUTDOWN_BUDGET);
        }
    }

    private void writeMockConfig(String serverName) throws Exception {
        Files.createDirectories(userConfigDir);
        String javaBin = yamlPath(McpSessionManagerTest.javaCommand());
        String classpath = yamlPath(System.getProperty("java.class.path"));
        Files.writeString(
            userConfigDir.resolve("config.yaml"),
            """
            mcp_servers:
              %s:
                type: stdio
                command: "%s"
                args:
                  - "-cp"
                  - "%s"
                  - "com.lavendercode.core.mcp.MockStdioMcpServer"
            """
                .formatted(serverName, javaBin, classpath));
    }

    private static String yamlPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private static String yamlPath(String path) {
        return path.replace("\\", "/");
    }
}
