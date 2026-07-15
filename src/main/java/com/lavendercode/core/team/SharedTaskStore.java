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
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class SharedTaskStore {
    private static final ObjectMapper JSON = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final long STALE_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int MAX_TRIES = 10;

    private final Path configDir;
    private final Path tasksPath;
    private final Path lockPath;

    public SharedTaskStore(Path teamConfigDir) {
        this.configDir = teamConfigDir;
        this.tasksPath = teamConfigDir.resolve("tasks.json");
        this.lockPath = teamConfigDir.resolve("tasks.lock");
    }

    public String create(String title, String description, String assignee, List<String> blockedBy) {
        return withLock(() -> {
            TaskFile file = read();
            String id = "task_" + String.format("%06x", ThreadLocalRandom.current().nextInt(1 << 24));
            long now = System.currentTimeMillis();
            List<String> blockers = blockedBy == null ? List.of() : List.copyOf(blockedBy);
            SharedTask task = new SharedTask(
                id, title, description == null ? "" : description,
                "pending", assignee, blockers, new ArrayList<>(), now, now);
            file.tasks.add(task);
            for (String b : blockers) {
                updateBlocksEdge(file, b, id, true);
            }
            write(file);
            return id;
        });
    }

    public Optional<SharedTask> get(String taskId) {
        return read().tasks.stream().filter(t -> t.id().equals(taskId)).findFirst();
    }

    public List<SharedTask> list(String statusFilter) {
        TaskFile file = read();
        List<SharedTask> out = new ArrayList<>();
        for (SharedTask t : file.tasks) {
            if (statusFilter != null && !statusFilter.equals(t.status())) {
                continue;
            }
            out.add(t.withReady(isReady(file, t)));
        }
        return out;
    }

    public SharedTask update(
            String taskId,
            String title,
            String description,
            String status,
            String assignee,
            List<String> addBlocks,
            List<String> addBlockedBy,
            List<String> removeBlocks,
            List<String> removeBlockedBy) {
        return withLock(() -> {
            TaskFile file = read();
            int idx = indexOf(file, taskId);
            if (idx < 0) {
                throw new IllegalArgumentException("任务不存在: " + taskId);
            }
            SharedTask t = file.tasks.get(idx);
            String newTitle = title != null ? title : t.title();
            String newDesc = description != null ? description : t.description();
            String newStatus = status != null ? status : t.status();
            String newAssignee = assignee != null ? assignee : t.assignee();
            List<String> blocks = new ArrayList<>(t.blocks());
            List<String> blockedBy = new ArrayList<>(t.blockedBy());
            if (addBlocks != null) {
                for (String id : addBlocks) {
                    if (!blocks.contains(id)) {
                        blocks.add(id);
                    }
                    updateBlockedByEdge(file, id, taskId, true);
                }
            }
            if (removeBlocks != null) {
                for (String id : removeBlocks) {
                    blocks.remove(id);
                    updateBlockedByEdge(file, id, taskId, false);
                }
            }
            if (addBlockedBy != null) {
                for (String id : addBlockedBy) {
                    if (!blockedBy.contains(id)) {
                        blockedBy.add(id);
                    }
                    updateBlocksEdge(file, id, taskId, true);
                }
            }
            if (removeBlockedBy != null) {
                for (String id : removeBlockedBy) {
                    blockedBy.remove(id);
                    updateBlocksEdge(file, id, taskId, false);
                }
            }
            SharedTask updated = new SharedTask(
                t.id(), newTitle, newDesc, newStatus, newAssignee,
                blockedBy, blocks, t.createdAt(), System.currentTimeMillis());
            file.tasks.set(idx, updated);
            write(file);
            return updated.withReady(isReady(file, updated));
        });
    }

    private static boolean isReady(TaskFile file, SharedTask t) {
        for (String b : t.blockedBy()) {
            SharedTask blocker = file.tasks.stream()
                .filter(x -> x.id().equals(b)).findFirst().orElse(null);
            if (blocker == null || !"completed".equals(blocker.status())) {
                return false;
            }
        }
        return true;
    }

    private static void updateBlocksEdge(TaskFile file, String fromId, String toId, boolean add) {
        int i = indexOf(file, fromId);
        if (i < 0) {
            return;
        }
        SharedTask t = file.tasks.get(i);
        List<String> blocks = new ArrayList<>(t.blocks());
        if (add) {
            if (!blocks.contains(toId)) {
                blocks.add(toId);
            }
        } else {
            blocks.remove(toId);
        }
        file.tasks.set(i, new SharedTask(
            t.id(), t.title(), t.description(), t.status(), t.assignee(),
            t.blockedBy(), blocks, t.createdAt(), System.currentTimeMillis()));
    }

    private static void updateBlockedByEdge(TaskFile file, String targetId, String fromId, boolean add) {
        int i = indexOf(file, targetId);
        if (i < 0) {
            return;
        }
        SharedTask t = file.tasks.get(i);
        List<String> blockedBy = new ArrayList<>(t.blockedBy());
        if (add) {
            if (!blockedBy.contains(fromId)) {
                blockedBy.add(fromId);
            }
        } else {
            blockedBy.remove(fromId);
        }
        file.tasks.set(i, new SharedTask(
            t.id(), t.title(), t.description(), t.status(), t.assignee(),
            blockedBy, t.blocks(), t.createdAt(), System.currentTimeMillis()));
    }

    private static int indexOf(TaskFile file, String id) {
        for (int i = 0; i < file.tasks.size(); i++) {
            if (file.tasks.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private TaskFile read() {
        if (!Files.exists(tasksPath)) {
            return new TaskFile();
        }
        try {
            return JSON.readValue(tasksPath.toFile(), TaskFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取 tasks.json 失败", e);
        }
    }

    private void write(TaskFile file) throws IOException {
        Files.createDirectories(configDir);
        Path tmp = tasksPath.resolveSibling("tasks.json.tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), file);
        try {
            Files.move(tmp, tasksPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, tasksPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> T withLock(IoSupplier<T> action) {
        try {
            Files.createDirectories(configDir);
            for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
                try {
                    Files.newOutputStream(lockPath, StandardOpenOption.CREATE_NEW).close();
                    try {
                        return action.get();
                    } finally {
                        Files.deleteIfExists(lockPath);
                    }
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    if (Files.exists(lockPath)) {
                        FileTime t = Files.getLastModifiedTime(lockPath);
                        if (System.currentTimeMillis() - t.toMillis() > STALE_MS) {
                            Files.deleteIfExists(lockPath);
                            continue;
                        }
                    }
                    Thread.sleep(5 + ThreadLocalRandom.current().nextInt(96));
                }
            }
            throw new IllegalStateException("tasks.lock 获取失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class TaskFile {
        public List<SharedTask> tasks = new ArrayList<>();
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
