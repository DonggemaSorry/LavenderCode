package com.lavendercode.core.tool;

import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.task.TaskStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TaskListTool implements Tool {

    private final TaskManager taskManager;

    public TaskListTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String name() {
        return "TaskList";
    }

    @Override
    public String description() {
        return "List active background sub-agent tasks.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object", Map.of(), List.of());
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (taskManager == null) {
            return ToolResult.error("NO_TASK_MANAGER", "TaskManager 未配置", "");
        }
        String summary = taskManager.list().stream()
            .filter(t -> !t.isTerminated())
            .map(this::formatLine)
            .collect(Collectors.joining("\n"));
        if (summary.isBlank()) {
            summary = "(no active tasks)";
        }
        return ToolResult.success("task list", summary);
    }

    private String formatLine(BackgroundTask t) {
        return t.id() + " name=" + t.name() + " status=" + t.status();
    }
}
