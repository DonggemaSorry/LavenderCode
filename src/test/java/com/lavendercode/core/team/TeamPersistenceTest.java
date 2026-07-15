package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamPersistenceTest {
    @TempDir Path tmp;

    @Test
    void addMemberPersistsAtomicallyAndReloadsFromDisk() throws Exception {
        Path configDir = tmp.resolve("demo");
        Files.createDirectories(configDir);
        Team team = new Team(
            "demo", "demo", "lead", BackendType.IN_PROCESS,
            "", Instant.now(), configDir, configDir.resolve("config.json"),
            new ArrayList<>(List.of(TeammateInfo.leadPlaceholder())));
        team.addMember(new TeammateInfo(
            "alice", "agent-1", "general-purpose", "",
            tmp.resolve("wt"), "worktree-team-demo+alice",
            BackendType.IN_PROCESS, "", true, false, tmp.resolve("sess")));
        assertThat(Files.exists(team.configPath())).isTrue();
        String json = Files.readString(team.configPath());
        assertThat(json).contains("alice").contains("agent-1");

        Team onDisk = Team.load(team.configPath());
        onDisk.setMemberActive("alice", false);

        team.reloadFromDiskLocked();
        assertThat(team.findMember("alice").orElseThrow().isActive()).isFalse();
    }
}
