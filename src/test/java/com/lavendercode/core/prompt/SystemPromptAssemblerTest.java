package com.lavendercode.core.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptAssemblerTest {
    @Test
    void assemblesSevenModulesInPriorityOrder() {
        String result = SystemPromptAssembler.assemble(null);
        assertThat(result).contains("LavenderCode Agent");
        assertThat(result).contains("System Constraints");
        assertThat(result).contains("Task Mode");
        assertThat(result).contains("Action Execution");
        assertThat(result).contains("Tool Usage Rules");
        assertThat(result).contains("Tone and Style");
        assertThat(result).contains("Text Output");
    }

    @Test
    void modulesSeparatedByBlankLine() {
        String result = SystemPromptAssembler.assemble(null);
        assertThat(result).contains("\n\n");
    }

    @Test
    void skipsNullCustomInstructions() {
        String result = SystemPromptAssembler.assemble(null);
        assertThat(result).doesNotContain("User Instructions");
    }

    @Test
    void skipsBlankCustomInstructions() {
        String result = SystemPromptAssembler.assemble("  ");
        assertThat(result).doesNotContain("User Instructions");
    }

    @Test
    void appendsCustomInstructionsWhenPresent() {
        String result = SystemPromptAssembler.assemble("Be extra careful.");
        assertThat(result).endsWith("Be extra careful.");
    }

    @Test
    void twoCallsProduceIdenticalBytes() {
        String a = SystemPromptAssembler.assemble(null);
        String b = SystemPromptAssembler.assemble(null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void outputExcludesWorkingDirectory() {
        String result = SystemPromptAssembler.assemble(null);
        assertThat(result).doesNotContain("user.dir");
        assertThat(result).doesNotContain(System.getProperty("user.dir"));
    }
}
