package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.lavendercode.core.task.AgentNameRegistry;
import com.lavendercode.core.task.TaskManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentTeamInProcessTest {
    @TempDir Path home;

    @AfterEach
    void clear() {
        System.clearProperty("LAVENDERCODE_TEAM_BACKEND");
    }

    @Test
    void inProcessTeamCreateSpawnIdleResume() throws Exception {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TaskManager tm = new TaskManager();
        TeamManager mgr = new TeamManager(home, null, tm, new AgentNameRegistry());
        Team team = mgr.create("inproc", "");
        assertThat(team.backend()).isEqualTo(BackendType.IN_PROCESS);

        AtomicInteger runs = new AtomicInteger();
        CountDownLatch done1 = new CountDownLatch(1);
        Path marker1 = home.resolve("m1.txt");
        Path marker2 = home.resolve("m2.txt");

        var backend = new InProcessBackend(tm);
        var result = backend.spawn(new SpawnRequest(
            team.sanitizedName(), "bob", "", home, home.resolve("sess"),
            "", "", "first", false, () -> {
                runs.incrementAndGet();
                Files.writeString(marker1, "1");
                done1.countDown();
                return "ok";
            }));
        assertThat(done1.await(5, TimeUnit.SECONDS)).isTrue();
        team.addMember(new TeammateInfo(
            "bob", result.agentId(), "", "", home, "b",
            BackendType.IN_PROCESS, "", false, false, home.resolve("sess")));
        mgr.registry().register("bob", result.agentId());

        Mailbox box = new Mailbox(team.configDir());
        box.write("lead", MailMessage.text("bob", "lead", "bob idle", "done"));
        assertThat(box.claimUnread("lead")).isNotEmpty();

        CountDownLatch done2 = new CountDownLatch(1);
        String id2 = tm.launch(() -> {
            runs.incrementAndGet();
            Files.writeString(marker2, "2");
            done2.countDown();
            return "ok2";
        }, "bob");
        assertThat(done2.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(marker1)).isTrue();
        assertThat(Files.exists(marker2)).isTrue();
        assertThat(runs.get()).isEqualTo(2);
        assertThat(id2).isNotBlank();
    }

    @Test
    void migrateUnreadMovesMessagesToNewAgentId() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager mgr = new TeamManager(home, null, new TaskManager(), new AgentNameRegistry());
        Team team = mgr.create("mig", "");
        Mailbox box = new Mailbox(team.configDir());
        box.write("old-id", MailMessage.text("lead", "old-id", "wake", "please continue"));
        box.migrateUnread("old-id", "new-id");
        assertThat(box.readUnread("old-id")).isEmpty();
        assertThat(box.readUnread("new-id")).hasSize(1);
        assertThat(box.readUnread("new-id").get(0).summary()).isEqualTo("wake");
    }
}
