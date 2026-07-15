package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lavendercode.core.task.AgentNameRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamManagerTest {
    @TempDir Path home;

    @AfterEach
    void clearForce() {
        System.clearProperty("LAVENDERCODE_TEAM_BACKEND");
    }

    private TeamManager mgr() {
        return new TeamManager(home, null, null, new AgentNameRegistry());
    }

    @Test
    void createWritesConfigUnderLavendercodeTeams() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        Team t = mgr().create("refactor auth", "");
        assertThat(t.sanitizedName()).isEqualTo("refactor-auth");
        Path cfg = home.resolve(".lavendercode/teams/refactor-auth/config.json");
        assertThat(Files.exists(cfg)).isTrue();
        assertThat(t.backend()).isEqualTo(BackendType.IN_PROCESS);
    }

    @Test
    void duplicateNameGetsSuffix() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        m.create("demo", "");
        Team t2 = m.create("demo", "");
        assertThat(t2.sanitizedName()).isEqualTo("demo-2");
    }

    @Test
    void deleteWithoutForceRejectsActiveMember() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        Team t = m.create("x", "");
        t.addMember(new TeammateInfo("a", "id1", "", "", home, "b",
            BackendType.IN_PROCESS, "", true, false, home));
        assertThatThrownBy(() -> m.delete("x", false))
            .isInstanceOf(TeamHasActiveMembersException.class);
        assertThat(Files.exists(t.configDir())).isTrue();
    }

    @Test
    void forceDeleteRemovesConfigDir() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        Team t = m.create("y", "");
        m.delete("y", true);
        assertThat(Files.exists(t.configDir())).isFalse();
    }
}
