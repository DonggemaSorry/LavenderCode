package com.lavendercode.core.tool;

import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.task.TaskStatus;
import java.util.List;
import java.util.Map;

public final class SendMessageTool implements Tool {

    private final TaskManager taskManager;
    private final com.lavendercode.core.subagent.SubAgentServices subAgentServices;

    public SendMessageTool(TaskManager taskManager,
                           com.lavendercode.core.subagent.SubAgentServices subAgentServices) {
        this.taskManager = taskManager;
        this.subAgentServices = subAgentServices;
    }

    @Override
    public String name() {
        return "SendMessage";
    }

    @Override
    public String description() {
        return "Send a follow-up message to a completed background task by name.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "name", new ToolParameterSchema.PropertyDef(
                    "string", "Task name", null, null),
                "message", new ToolParameterSchema.PropertyDef(
                    "string", "Follow-up message", null, null)),
            List.of("name", "message"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (taskManager == null || subAgentServices == null) {
            return ToolResult.error("NO_TASK_MANAGER", "TaskManager 未配置", "");
        }
        String name = stringParam(params, "name");
        String message = stringParam(params, "message");
        if (name == null || name.isBlank()) {
            return ToolResult.error("VALIDATION", "name 必填", "");
        }
        if (message == null || message.isBlank()) {
            return ToolResult.error("VALIDATION", "message 必填", "");
        }
        BackgroundTask task = taskManager.findByName(name);
        if (task == null || task.conversation() == null) {
            return ToolResult.error("NOT_FOUND", "未找到可续聊的已完成任务: " + name, "");
        }
        var cancelFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
        var conversationOut = new java.util.concurrent.atomic.AtomicReference<java.util.List<com.lavendercode.core.provider.Message>>();
        var def = com.lavendercode.core.subagent.AgentDefinition.forkBase("");
        String newTaskId = taskManager.launch(
            com.lavendercode.core.subagent.SubAgentLauncher.buildWork(
                subAgentServices, def, message, true, true,
                task.conversation(), cancelFlag, conversationOut),
            name + "-followup",
            task.conversation(),
            conversationOut);
        return ToolResult.success("relaunched", "{\"task_id\":\"" + newTaskId + "\",\"status\":\"async_launched\"}");
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
