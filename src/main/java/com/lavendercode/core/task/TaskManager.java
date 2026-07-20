package com.lavendercode.core.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class TaskManager {

    private final ConcurrentHashMap<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private final CopyOnWriteArrayList<Consumer<BackgroundTask>> doneListeners = new CopyOnWriteArrayList<>();

    public String launch(Callable<String> work, String name) {
        return launch(work, name, null);
    }

    public String launch(Callable<String> work, String name, List<com.lavendercode.core.provider.Message> conversation) {
        return launch(work, name, conversation, null);
    }

    public String launch(Callable<String> work, String name,
                         List<com.lavendercode.core.provider.Message> conversation,
                         java.util.concurrent.atomic.AtomicReference<List<com.lavendercode.core.provider.Message>> conversationOut) {
        return launch(work, name, conversation, conversationOut, null);
    }

    public String launch(Callable<String> work, String name,
                         List<com.lavendercode.core.provider.Message> conversation,
                         java.util.concurrent.atomic.AtomicReference<List<com.lavendercode.core.provider.Message>> conversationOut,
                         AtomicBoolean cancelFlag) {
        String id = "task-" + seq.incrementAndGet();
        AtomicBoolean cancel = cancelFlag != null ? cancelFlag : new AtomicBoolean(false);
        Instant start = Instant.now();
        BackgroundTask initial = new BackgroundTask(
            id, name != null ? name : id, TaskStatus.RUNNING, start, null, null, null, cancel, conversation);
        tasks.put(id, initial);

        Thread.startVirtualThread(() -> runWork(id, work, cancel, start, initial.name(), conversation, conversationOut));
        return id;
    }

    private void runWork(String id, Callable<String> work, AtomicBoolean cancel,
                         Instant start, String name,
                         List<com.lavendercode.core.provider.Message> conversation,
                         java.util.concurrent.atomic.AtomicReference<List<com.lavendercode.core.provider.Message>> conversationOut) {
        String result = null;
        String error = null;
        TaskStatus status;
        try {
            result = work.call();
            status = cancel.get() ? TaskStatus.CANCELLED : TaskStatus.COMPLETED;
        } catch (Exception e) {
            if (cancel.get()) {
                status = TaskStatus.CANCELLED;
            } else {
                status = TaskStatus.FAILED;
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        } catch (Error e) {
            status = cancel.get() ? TaskStatus.CANCELLED : TaskStatus.FAILED;
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            List<com.lavendercode.core.provider.Message> finalConv = conversation;
            if (conversationOut != null && conversationOut.get() != null) {
                finalConv = conversationOut.get();
            }
            BackgroundTask done = new BackgroundTask(
                id, name, status, start, Instant.now(), result, error, cancel, finalConv);
            tasks.put(id, done);
            notifyDone(done);
            throw e;
        }
        List<com.lavendercode.core.provider.Message> finalConv = conversation;
        if (conversationOut != null && conversationOut.get() != null) {
            finalConv = conversationOut.get();
        }
        BackgroundTask done = new BackgroundTask(
            id, name, status, start, Instant.now(), result, error, cancel, finalConv);
        tasks.put(id, done);
        notifyDone(done);
    }

    public String adoptRunning(String name, AtomicBoolean cancelFlag, Callable<String> continuation,
                               List<com.lavendercode.core.provider.Message> conversation) {
        return adoptRunning(name, cancelFlag, continuation, conversation, null);
    }

    public String adoptRunning(String name, AtomicBoolean cancelFlag, Callable<String> continuation,
                               List<com.lavendercode.core.provider.Message> conversation,
                               java.util.concurrent.atomic.AtomicReference<List<com.lavendercode.core.provider.Message>> conversationOut) {
        return launch(continuation, name, conversation, conversationOut);
    }

    public BackgroundTask get(String id) {
        return tasks.get(id);
    }

    public List<BackgroundTask> list() {
        return new ArrayList<>(tasks.values());
    }

    public void stop(String id) {
        BackgroundTask task = tasks.get(id);
        if (task != null && task.cancelFlag() != null) {
            task.cancelFlag().set(true);
        }
    }

    public BackgroundTask findByName(String name) {
        if (name == null) {
            return null;
        }
        return tasks.values().stream()
            .filter(t -> name.equals(t.name()) && t.status() == TaskStatus.COMPLETED)
            .reduce((a, b) -> b)
            .orElse(null);
    }

    public void subscribeDone(Consumer<BackgroundTask> listener) {
        doneListeners.add(listener);
    }

    private void notifyDone(BackgroundTask task) {
        for (Consumer<BackgroundTask> listener : doneListeners) {
            try {
                listener.accept(task);
            } catch (Exception e) {
                System.err.println("WARN: task done listener failed: " + e.getMessage());
            }
        }
    }
}
