package com.lavendercode.core.subagent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentDefinitionParserTest {

    @Test
    void parsesFrontmatterAndBody() {
        String md = """
            ---
            name: explore
            description: Read-only exploration
            disallowedTools: [write_file, edit_file]
            model: haiku
            maxTurns: 30
            permissionMode: default
            ---
            You are an explorer.
            """;
        AgentDefinition def = AgentDefinitionParser.parse(md, AgentCatalog.Source.BUILTIN);
        assertThat(def.name()).isEqualTo("explore");
        assertThat(def.description()).isEqualTo("Read-only exploration");
        assertThat(def.disallowedTools()).containsExactly("write_file", "edit_file");
        assertThat(def.model()).isEqualTo("haiku");
        assertThat(def.maxTurns()).isEqualTo(30);
        assertThat(def.systemPrompt()).isEqualTo("You are an explorer.");
    }

    @Test
    void unknownModelFallsBackToInherit() {
        String md = """
            ---
            name: bad
            description: d
            model: not-a-model
            ---
            body
            """;
        AgentDefinition def = AgentDefinitionParser.parse(md, AgentCatalog.Source.USER);
        assertThat(def.model()).isEqualTo("inherit");
    }

    @Test
    void parsesIsolationWorktree() {
        String md = """
            ---
            name: coder
            description: d
            isolation: worktree
            ---
            body
            """;
        assertThat(AgentDefinitionParser.parse(md, AgentCatalog.Source.USER).isolation())
            .isEqualTo("worktree");
    }

    @Test
    void invalidIsolationFallsBackEmpty() {
        String md = """
            ---
            name: coder
            description: d
            isolation: sandbox
            ---
            body
            """;
        assertThat(AgentDefinitionParser.parse(md, AgentCatalog.Source.USER).isolation())
            .isEqualTo("");
    }
}
