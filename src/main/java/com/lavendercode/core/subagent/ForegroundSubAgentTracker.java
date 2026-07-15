package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.task.TaskManager;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ForegroundSubAgentTracker {

    private volatile ForegroundRun current;

    public record ForegroundRun(
        String description,
        AgentDefinition def,
        String prompt,
        AtomicBoolean cancelFlag,
        CompletableFuture<String> future,
        ScheduledFuture<?> timeoutFuture,
        AtomicReference<List<Message>> conversationOut
    ) {}

    public void register(ForegroundRun run) {
        this.current = run;
    }

    public ForegroundRun current() {
        return current;
    }

    public boolean isActive() {
        ForegroundRun run = current;
        return run != null && run.future() != null && !run.future().isDone();
    }

    public void clear() {
        current = null;
    }

    public String adoptToBackground(TaskManager taskManager, String name) {
        ForegroundRun run = current;
        if (run == null || run.future() == null || run.future().isDone()) {
            return null;
        }
        if (run.timeoutFuture() != null) {
            run.timeoutFuture().cancel(false);
        }
        List<Message> seed = run.conversationOut() != null ? run.conversationOut().get() : null;
        String taskId = taskManager.adoptRunning(
            name != null ? name : run.description(),
            run.cancelFlag(),
            () -> {
                try {
                    return run.future().get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },
            seed,
            run.conversationOut());
        current = null;
        return taskId;
    }
}
