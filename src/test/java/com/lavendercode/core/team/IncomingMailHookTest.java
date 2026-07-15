package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncomingMailHookTest {
    @TempDir Path dir;

    @AfterEach
    void clear() {
        IncomingMailHook.clear();
        PlanApprovalState.clear();
    }

    @Test
    void pollFormatsUnreadAndMarksRead() {
        Team team = new Team(
            "t", "t", "lead", BackendType.IN_PROCESS, "",
            java.time.Instant.now(), dir, dir.resolve("config.json"),
            new java.util.ArrayList<>());
        team.saveAtomic();
        Mailbox box = new Mailbox(dir);
        box.write("aid", MailMessage.text("lead", "aid", "hi there", "hello body"));
        IncomingMailHook.set(IncomingMailHook.forTeammate(team, "aid"));
        Optional<String> rem = IncomingMailHook.poll();
        assertThat(rem).isPresent();
        assertThat(rem.get()).contains("<incoming-messages>").contains("hi there");
        assertThat(box.readUnread("aid")).isEmpty();
    }
}
