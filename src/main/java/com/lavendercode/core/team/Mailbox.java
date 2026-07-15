package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class Mailbox {
    private static final ObjectMapper JSON = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final long STALE_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int MAX_TRIES = 10;
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> JVM_LOCKS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final Path root;

    public Mailbox(Path teamConfigDir) {
        this.root = teamConfigDir.resolve("mailbox");
    }

    public Path root() {
        return root;
    }

    public void write(String agentId, MailMessage msg) {
        withLock(agentId, () -> {
            MailFile file = readFile(agentId);
            file.messages.add(msg);
            writeFile(agentId, file);
            return null;
        });
    }

    public List<MailMessage> readUnread(String agentId) {
        MailFile file = readFileUnlocked(agentId);
        List<MailMessage> unread = new ArrayList<>();
        for (MailMessage m : file.messages) {
            if (!m.read()) {
                unread.add(m);
            }
        }
        return unread;
    }

    public List<MailMessage> readAll(String agentId) {
        return List.copyOf(readFileUnlocked(agentId).messages);
    }

    public void markRead(String agentId, List<Integer> indices) {
        withLock(agentId, () -> {
            MailFile file = readFile(agentId);
            for (int idx : indices) {
                if (idx >= 0 && idx < file.messages.size()) {
                    MailMessage old = file.messages.get(idx);
                    file.messages.set(idx, old.withRead(true));
                }
            }
            writeFile(agentId, file);
            return null;
        });
    }

    /** 将当前所有未读标为已读。 */
    public void markAllUnreadAsRead(String agentId) {
        withLock(agentId, () -> {
            MailFile file = readFile(agentId);
            for (int i = 0; i < file.messages.size(); i++) {
                MailMessage m = file.messages.get(i);
                if (!m.read()) {
                    file.messages.set(i, m.withRead(true));
                }
            }
            writeFile(agentId, file);
            return null;
        });
    }

    private <T> T withLock(String agentId, IoSupplier<T> action) {
        Object monitor = JVM_LOCKS.computeIfAbsent(agentId, k -> new Object());
        synchronized (monitor) {
            try {
                Files.createDirectories(root);
                Path lock = lockPath(agentId);
                for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
                    try {
                        Files.newOutputStream(lock, StandardOpenOption.CREATE_NEW).close();
                        try {
                            return action.get();
                        } finally {
                            Files.deleteIfExists(lock);
                        }
                    } catch (java.nio.file.FileAlreadyExistsException e) {
                        if (isStale(lock)) {
                            Files.deleteIfExists(lock);
                            continue;
                        }
                        long sleep = 20 + ThreadLocalRandom.current().nextInt(80);
                        Thread.sleep(sleep);
                    }
                }
                throw new IllegalStateException("邮箱锁获取失败: " + agentId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("邮箱锁被中断", e);
            } catch (IOException e) {
                throw new IllegalStateException("邮箱写入失败: " + e.getMessage(), e);
            }
        }
    }

    private boolean isStale(Path lock) throws IOException {
        if (!Files.exists(lock)) {
            return false;
        }
        FileTime t = Files.getLastModifiedTime(lock);
        return System.currentTimeMillis() - t.toMillis() > STALE_MS;
    }

    private MailFile readFile(String agentId) throws IOException {
        return readFileUnlocked(agentId);
    }

    private MailFile readFileUnlocked(String agentId) {
        Path path = mailboxPath(agentId);
        if (!Files.exists(path)) {
            return new MailFile();
        }
        try {
            return JSON.readValue(path.toFile(), MailFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取邮箱失败: " + path, e);
        }
    }

    private void writeFile(String agentId, MailFile file) throws IOException {
        Path path = mailboxPath(agentId);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), file);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path mailboxPath(String agentId) {
        return root.resolve(agentId + ".json");
    }

    private Path lockPath(String agentId) {
        return root.resolve(agentId + ".lock");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MailFile {
        public List<MailMessage> messages = new ArrayList<>();
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
