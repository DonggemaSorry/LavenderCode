package com.lavendercode.core.subagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class AgentCatalogTest {

    @Test
    void loadsBuiltinAgents() {
        var catalog = new AgentCatalog();
        catalog.loadBuiltinFromClasspath();
        assertThat(catalog.resolve("explore")).isNotNull();
        assertThat(catalog.resolve("general-purpose")).isNotNull();
        assertThat(catalog.resolve("plan")).isNotNull();
    }

    @Test
    void projectOverridesBuiltin(@TempDir Path workDir) throws Exception {
        Path projectDir = workDir.resolve(".lavendercode/agents");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("explore.md"), """
            ---
            name: explore
            description: project explore
            ---
            project body
            """);

        var catalog = new AgentCatalog();
        catalog.loadBuiltinFromClasspath();
        catalog.loadFromDirectory(
            Path.of(System.getProperty("user.home")).resolve(".lavendercode/agents"),
            AgentCatalog.Source.USER);
        catalog.loadFromDirectory(projectDir, AgentCatalog.Source.PROJECT);

        AgentDefinition def = catalog.resolve("explore");
        assertThat(def.source()).isEqualTo(AgentCatalog.Source.PROJECT);
        assertThat(def.description()).isEqualTo("project explore");
    }

    @Test
    void resolveUnknownReturnsNull() {
        var catalog = new AgentCatalog();
        assertThat(catalog.resolve("missing")).isNull();
    }
}
