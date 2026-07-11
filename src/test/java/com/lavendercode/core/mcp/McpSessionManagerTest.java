package com.lavendercode.core.mcp;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class McpSessionManagerTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ToolRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void connectsToMockStdioServerAndRegistersTool() throws Exception {
        McpServerConfig config = mockServerConfig(false);
        McpSessionManager manager = new McpSessionManager();
        try {
            manager.connectAndRegisterTools(config);
            assertThat(ToolRegistry.has("mcp__mock__echo")).isTrue();
            Tool tool = ToolRegistry.get("mcp__mock__echo");
            assertThat(tool).isNotNull();
            assertThat(tool.execute(java.util.Map.of("message", "hi")).content()).isEqualTo("echo:hi");
        } finally {
            manager.closeAll(McpConstants.SHUTDOWN_BUDGET);
        }
    }

    @Test
    void readOnlyHintMapsToIsReadOnly() throws Exception {
        McpServerConfig config = mockServerConfig(true);
        McpSessionManager manager = new McpSessionManager();
        try {
            manager.connectAndRegisterTools(config);
            Tool tool = ToolRegistry.get("mcp__mock__echo");
            assertThat(tool).isNotNull();
            assertThat(tool.isReadOnly()).isTrue();
        } finally {
            manager.closeAll(McpConstants.SHUTDOWN_BUDGET);
        }
    }

    private McpServerConfig mockServerConfig(boolean readOnly) throws Exception {
        Path javaBin = javaCommand();
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

    static Path javaCommand() {
        String name = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name);
    }
}
