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

    @Test
    void claimUnreadDoesNotLoseMessageWrittenDuringPoll() throws Exception {
        Mailbox box = new Mailbox(dir);
        box.write("a", MailMessage.text("lead", "a", "first", "1"));

        var claimed = new java.util.concurrent.CopyOnWriteArrayList<MailMessage>();
        CountDownLatch writerReady = new CountDownLatch(1);
        CountDownLatch claimStarted = new CountDownLatch(1);

        Thread claimer = new Thread(() -> {
            // 自旋直到至少有一条可读，再与写入交错
            claimStarted.countDown();
            try {
                writerReady.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            claimed.addAll(box.claimUnread("a"));
        });
        Thread writer = new Thread(() -> {
            try {
                claimStarted.await();
                box.write("a", MailMessage.text("lead", "a", "second", "2"));
                writerReady.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        claimer.start();
        writer.start();
        claimer.join();
        writer.join();

        List<MailMessage> remaining = box.readUnread("a");
        int totalSeen = claimed.size() + remaining.size();
        assertThat(totalSeen).isEqualTo(2);
        assertThat(box.readAll("a")).hasSize(2);
    }
}
