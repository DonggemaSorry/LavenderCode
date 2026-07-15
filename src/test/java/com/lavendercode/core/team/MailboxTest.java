package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailboxTest {
    @TempDir Path dir;

    @Test
    void writeAppendsUnreadWithTimestamp() {
        Mailbox box = new Mailbox(dir);
        box.write("agent-a", MailMessage.text("lead", "agent-a", "hi", "hello"));
        List<MailMessage> unread = box.readUnread("agent-a");
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).read()).isFalse();
        assertThat(unread.get(0).timestamp()).isPositive();
        box.markAllUnreadAsRead("agent-a");
        assertThat(box.readUnread("agent-a")).isEmpty();
    }

    @Test
    void concurrentWritesAllPersist() throws Exception {
        Mailbox box = new Mailbox(dir);
        int n = 10;
        var pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                box.write("a", MailMessage.text("lead", "a", "s" + idx, "c" + idx));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(box.readAll("a")).hasSize(n);
    }
}
