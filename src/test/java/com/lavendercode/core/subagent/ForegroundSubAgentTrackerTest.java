package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.task.TaskStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ForegroundSubAgentTrackerTest {

    @Test
    void adoptToBackgroundPreservesConversationOut() throws Exception {
        var tracker = new ForegroundSubAgentTracker();
        var manager = new TaskManager();
        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicReference<List<Message>> convOut = new AtomicReference<>();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            convOut.set(List.of(new Message(Role.USER, "task"), new Message(Role.ASSISTANT, "done")));
            return "done";
        });

        tracker.register(new ForegroundSubAgentTracker.ForegroundRun(
            "desc",
            AgentDefinition.forkBase("b"),
            "prompt",
            cancel,
            future,
            null,
            convOut));

        String taskId = tracker.adoptToBackground(manager, "named");
        assertThat(taskId).isNotNull();

        Thread.sleep(200);
        BackgroundTask task = manager.get(taskId);
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.conversation()).isNotNull();
        assertThat(task.conversation()).extracting(Message::content)
            .contains("task", "done");
    }
}
