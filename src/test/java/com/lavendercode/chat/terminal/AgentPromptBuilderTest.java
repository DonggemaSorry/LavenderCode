package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentPromptBuilderTest {
    @Test
    void buildsWithAgentPromptOnly() {
        String p = AgentPromptBuilder.build(null);
        assertThat(p).contains("LavenderCode Agent");
        assertThat(p).contains("Capabilities");
        assertThat(p).contains("Rules");
    }

    @Test
    void appendsUserPrompt() {
        String p = AgentPromptBuilder.build("Be concise.");
        assertThat(p).contains("User Instructions").contains("Be concise.");
        assertThat(p).startsWith("You are");
    }

    @Test
    void handlesBlankUserPrompt() {
        String p = AgentPromptBuilder.build("  ");
        assertThat(p).doesNotContain("User Instructions");
    }
}
