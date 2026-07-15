package com.lavendercode.core.task;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class TaskManagerTest {

    @Test
    void launchCompletesInBackground() throws Exception {
        TaskManager mgr = new TaskManager();
        AtomicBoolean ran = new AtomicBoolean(false);
        String id = mgr.launch(() -> {
            ran.set(true);
            return "result text";
        }, "my-task");
        assertThat(mgr.get(id).status()).isEqualTo(TaskStatus.RUNNING);
        Thread.sleep(200);
        assertThat(ran).isTrue();
        assertThat(mgr.get(id).status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(mgr.get(id).result()).isEqualTo("result text");
    }

    @Test
    void stopSetsCancelled() throws Exception {
        TaskManager mgr = new TaskManager();
        String[] idRef = new String[1];
        idRef[0] = mgr.launch(() -> {
            for (int i = 0; i < 50; i++) {
                if (mgr.get(idRef[0]).cancelFlag().get()) {
                    return "stopped";
                }
                Thread.sleep(20);
            }
            return "done";
        }, "slow");
        mgr.stop(idRef[0]);
        Thread.sleep(300);
        assertThat(mgr.get(idRef[0]).status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void listReturnsAllTasks() {
        TaskManager mgr = new TaskManager();
        mgr.launch(() -> "a", "t1");
        mgr.launch(() -> "b", "t2");
        assertThat(mgr.list()).hasSize(2);
    }
}
