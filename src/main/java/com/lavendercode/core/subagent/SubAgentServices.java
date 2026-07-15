package com.lavendercode.core.subagent;

import com.lavendercode.chat.terminal.BatchingToolExecutor;
import com.lavendercode.chat.terminal.TaskNotificationQueue;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.permission.HitlGate;
import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.permission.PermissionPipeline;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.tool.ToolContext;
import com.lavendercode.core.worktree.WorktreeManager;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public record SubAgentServices(
    AgentCatalog catalog,
    LlmProvider provider,
    LlmConfig config,
    Path projectRoot,
    Options options,
    PermissionPipeline parentPipeline,
    HitlGate hitlGate,
    TaskManager taskManager,
    TaskNotificationQueue notificationQueue,
    ForegroundSubAgentTracker foregroundTracker,
    Supplier<List<Message>> parentMessagesSupplier,
    WorktreeManager worktreeManager
) {
    public SubAgentServices(
        AgentCatalog catalog,
        LlmProvider provider,
        LlmConfig config,
        Path projectRoot,
        Options options,
        PermissionPipeline parentPipeline,
        HitlGate hitlGate,
        TaskManager taskManager,
        TaskNotificationQueue notificationQueue,
        ForegroundSubAgentTracker foregroundTracker,
        Supplier<List<Message>> parentMessagesSupplier) {
        this(catalog, provider, config, projectRoot, options, parentPipeline, hitlGate,
            taskManager, notificationQueue, foregroundTracker, parentMessagesSupplier, null);
    }

    public SubAgentServices(
        AgentCatalog catalog,
        LlmProvider provider,
        LlmConfig config,
        SubAgentLoopRunner.ToolBatchExecutor toolExecutor,
        Path projectRoot) {
        this(catalog, provider, config, projectRoot, new Options(),
            null, (req, flag) -> com.lavendercode.core.permission.HitlChoice.ALLOW_ONCE,
            null, null, null, () -> List.of(), null);
    }

    public SubAgentLoopRunner.ToolBatchExecutor createToolExecutor(AgentDefinition def) {
        return createToolExecutor(def, ToolContext.empty());
    }

    public SubAgentLoopRunner.ToolBatchExecutor createToolExecutor(AgentDefinition def, ToolContext ctx) {
        PermissionMode mode = def.permissionMode() != null ? def.permissionMode() : PermissionMode.DEFAULT;
        var subPipeline = parentPipeline != null
            ? SubAgentPermissionPipeline.create(
                parentPipeline.ruleEngineLayer(), mode, hitlGate, projectRoot, def.name(), r -> {})
            : SubAgentPermissionPipeline.create(
                com.lavendercode.core.permission.RuleEngineLayer.ofRules(List.of()),
                mode, hitlGate, projectRoot, def.name(), r -> {});
        Options opts = options != null ? options : new Options();
        var batch = new BatchingToolExecutor(
            opts.fileOperationTimeoutSeconds(),
            opts.commandTimeoutSeconds(),
            subPipeline,
            projectRoot,
            null,
            ctx != null ? ctx : ToolContext.empty());
        return (calls, flag) -> batch.execute(calls, e -> {}, flag);
    }

    public SubAgentLoopRunner createRunner(AgentDefinition def) {
        return createRunner(def, ToolContext.empty());
    }

    public SubAgentLoopRunner createRunner(AgentDefinition def, ToolContext ctx) {
        return new SubAgentLoopRunner(provider, config, createToolExecutor(def, ctx), def.maxTurns());
    }

    public boolean hasForegroundSubAgent() {
        return foregroundTracker != null && foregroundTracker.isActive();
    }

    public List<Message> parentMessages() {
        return parentMessagesSupplier != null ? parentMessagesSupplier.get() : List.of();
    }
}
