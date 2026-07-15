package com.lavendercode.core.tool;

import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import java.util.List;
import java.util.Map;

public final class TaskGetTool implements Tool {

    private final TaskManager taskManager;

    public TaskGetTool(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String name() {
        return "TaskGet";
    }

    @Override
    public String description() {
        return "Get full status of a background task by task_id.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of("task_id", new ToolParameterSchema.PropertyDef(
                "string", "Task identifier", null, null)),
            List.of("task_id"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (taskManager == null) {
            return ToolResult.error("NO_TASK_MANAGER", "TaskManager 未配置", "");
        }
        String taskId = stringParam(params, "task_id");
        if (taskId == null || taskId.isBlank()) {
            return ToolResult.error("VALIDATION", "task_id 必填", "");
        }
        BackgroundTask task = taskManager.get(taskId);
        if (task == null) {
            return ToolResult.error("NOT_FOUND", "任务不存在: " + taskId, "");
        }
        String body = "id=" + task.id()
            + "\nname=" + task.name()
            + "\nstatus=" + task.status()
            + "\nresult=" + (task.result() != null ? task.result() : "")
            + "\nerror=" + (task.error() != null ? task.error() : "");
        return ToolResult.success("task " + taskId, body);
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
