package com.lavendercode.core.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class McpConfigLoaderTest {
    @TempDir
    Path tempHome;

    @TempDir
    Path projectRoot;

    @Test
    void mergesProjectOverUserByServerName() throws Exception {
        seedConfigs();
        List<McpServerConfig> servers = McpConfigLoader.load(projectRoot, tempHome);

        McpServerConfig shared = find(servers, "shared");
        assertThat(shared.type()).isEqualTo(McpServerType.STDIO);
        assertThat(shared.args()).containsExactly("project");

        assertThat(find(servers, "github").command()).isEqualTo("npx");
        assertThat(find(servers, "api").url()).isEqualTo("https://example.com/mcp");
    }

    @Test
    void skipsInvalidServerAndContinues() throws Exception {
        Path userFile = tempHome.resolve("config.yaml");
        Files.writeString(
            userFile,
            """
            mcp_servers:
              bad:
                type: stdio
              good:
                type: stdio
                command: echo
            """);
        List<McpServerConfig> servers = McpConfigLoader.load(projectRoot, tempHome);
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).name()).isEqualTo("good");
    }

    @Test
    void missingFilesYieldEmptyList() {
        assertThat(McpConfigLoader.load(projectRoot, tempHome)).isEmpty();
    }

    private void seedConfigs() throws Exception {
        Files.createDirectories(tempHome);
        Files.copy(
            getClass().getResourceAsStream("/mcp/user-config.yaml"),
            tempHome.resolve("config.yaml"));
        Files.copy(
            getClass().getResourceAsStream("/mcp/project-config.yaml"),
            projectRoot.resolve(".LavenderCode.yaml"));
    }

    private static McpServerConfig find(List<McpServerConfig> list, String name) {
        return list.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }
}
