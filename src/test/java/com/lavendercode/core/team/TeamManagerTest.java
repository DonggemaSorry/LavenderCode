package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lavendercode.core.task.AgentNameRegistry;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void forceDeleteStopsMemberBackgroundTask() throws Exception {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TaskManager tm = new TaskManager();
        TeamManager m = new TeamManager(home, null, tm, new AgentNameRegistry());
        Team t = m.create("z", "");
        AtomicBoolean cancelSeen = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        String[] idRef = new String[1];
        idRef[0] = tm.launch(() -> {
            started.countDown();
            for (int i = 0; i < 100; i++) {
                if (tm.get(idRef[0]).cancelFlag().get()) {
                    cancelSeen.set(true);
                    return "stopped";
                }
                Thread.sleep(20);
            }
            return "done";
        }, "bob");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        t.addMember(new TeammateInfo(
            "bob", idRef[0], "", "", home, "b",
            BackendType.IN_PROCESS, "", true, false, home));
        m.delete("z", true);
        Thread.sleep(300);
        assertThat(cancelSeen).isTrue();
        assertThat(tm.get(idRef[0]).status()).isEqualTo(TaskStatus.CANCELLED);
    }
}
