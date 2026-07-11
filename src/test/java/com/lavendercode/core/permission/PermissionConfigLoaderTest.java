package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionConfigLoaderTest {
    @Test
    void missingFilesYieldEmptyRules(@TempDir Path root) {
        var cfg = PermissionConfigLoader.load(root, root.resolve("nope-user"));
        assertThat(cfg.rules()).isEmpty();
        assertThat(cfg.defaultMode()).isEqualTo(PermissionMode.DEFAULT);
    }

    @Test
    void invalidYamlTierSkipped(@TempDir Path root) throws Exception {
        Path dir = root.resolve(".lavendercode");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("permissions.yaml"), ":\n  bad: [");
        var cfg = PermissionConfigLoader.load(root, root.resolve("nope-user"));
        assertThat(cfg.rules()).isEmpty();
    }

    @Test
    void defaultModeLocalOverridesProject(@TempDir Path root) throws Exception {
        Path dir = root.resolve(".lavendercode");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("permissions.yaml"), "defaultMode: acceptEdits\nrules: []\n");
        Files.writeString(dir.resolve("permissions.local.yaml"), "defaultMode: plan\nrules: []\n");
        var cfg = PermissionConfigLoader.load(root, root.resolve("nope-user"));
        assertThat(cfg.defaultMode()).isEqualTo(PermissionMode.PLAN);
    }

    @Test
    void loadsRulesFromYaml(@TempDir Path root) throws Exception {
        Path dir = root.resolve(".lavendercode");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("permissions.yaml"),
            "rules:\n  - \"Bash(git *)\": allow\n  - \"Bash(git push)\": deny\n");
        var cfg = PermissionConfigLoader.load(root, root.resolve("nope-user"));
        assertThat(cfg.projectRules()).hasSize(2);
    }
}
