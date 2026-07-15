package com.lavendercode.core.team;

import com.lavendercode.core.task.TaskManager;
import java.io.IOException;

public final class InProcessBackend implements Backend {
    private final TaskManager taskManager;

    public InProcessBackend(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public BackendType type() {
        return BackendType.IN_PROCESS;
    }

    @Override
    public SpawnResult spawn(SpawnRequest req) throws IOException {
        if (taskManager == null) {
            throw new IOException("TaskManager 未配置");
        }
        if (req.work() == null) {
            throw new IOException("in-process spawn 需要 work Callable");
        }
        String id = taskManager.launch(req.work(), req.memberName());
        return new SpawnResult("", id);
    }

    @Override
    public void wake(String paneId, String agentId) {
        // no-op
    }

    @Override
    public void kill(String paneId, String agentId) {
        if (taskManager != null && agentId != null) {
            taskManager.stop(agentId);
        }
    }
}
