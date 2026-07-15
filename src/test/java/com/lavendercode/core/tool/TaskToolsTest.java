package com.lavendercode.core.tool;

import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.task.TaskStatus;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TaskToolsTest {

    private TaskManager mgr;

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
        mgr = new TaskManager();
        ToolRegistry.register(new TaskListTool(mgr));
        ToolRegistry.register(new TaskGetTool(mgr));
        ToolRegistry.register(new TaskStopTool(mgr));
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void taskListShowsActiveTasks() throws Exception {
        mgr.launch(() -> {
            Thread.sleep(300);
            return "done";
        }, "active-one");
        TaskListTool listTool = new TaskListTool(mgr);
        ToolResult r = listTool.execute(Map.of());
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("active-one");
        assertThat(r.content()).contains("RUNNING");
    }

    @Test
    void taskGetReturnsDetails() {
        String id = mgr.launch(() -> "result", "named");
        TaskGetTool getTool = new TaskGetTool(mgr);
        ToolResult r = getTool.execute(Map.of("task_id", id));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("named");
    }

    @Test
    void taskStopRequestsCancellation() throws Exception {
        String[] idRef = new String[1];
        idRef[0] = mgr.launch(() -> {
            for (int i = 0; i < 50; i++) {
                if (mgr.get(idRef[0]).cancelFlag().get()) {
                    return "cancelled";
                }
                Thread.sleep(20);
            }
            return "done";
        }, "stoppable");
        TaskStopTool stopTool = new TaskStopTool(mgr);
        ToolResult r = stopTool.execute(Map.of("task_id", idRef[0]));
        assertThat(r.success()).isTrue();
        Thread.sleep(300);
        assertThat(mgr.get(idRef[0]).status()).isEqualTo(TaskStatus.CANCELLED);
    }
}
