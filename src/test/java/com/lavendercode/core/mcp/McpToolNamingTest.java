package com.lavendercode.core.mcp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class McpToolNamingTest {
    @Test
    void registryNameUsesDoubleUnderscorePrefix() {
        assertThat(McpToolNaming.registryName("github", "create_issue"))
            .isEqualTo("mcp__github__create_issue");
    }

    @Test
    void rejectsInvalidCharactersAfterPrefix() {
        assertThat(McpToolNaming.isValidRegistryName("mcp__gh__tool.name")).isFalse();
        assertThat(McpToolNaming.isValidRegistryName("mcp__gh__tool_ok")).isTrue();
    }
}
