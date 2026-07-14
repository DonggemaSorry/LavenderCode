package com.lavendercode.chat.terminal;

import com.lavendercode.core.hook.*;
import com.lavendercode.core.permission.*;
import com.lavendercode.core.tool.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchingToolExecutor {
    private final long defaultTimeoutSec;
    private final long commandTimeoutSec;
    private final PermissionPipeline pipeline;
    private final Path projectRoot;
    private final HookEngine hookEngine;

    public BatchingToolExecutor(long defaultTimeoutSec, long commandTimeoutSec,
                                PermissionPipeline pipeline, Path projectRoot) {
        this(defaultTimeoutSec, commandTimeoutSec, pipeline, projectRoot, null);
    }

    public BatchingToolExecutor(long defaultTimeoutSec, long commandTimeoutSec,
                                PermissionPipeline pipeline, Path projectRoot,
                                HookEngine hookEngine) {
        this.defaultTimeoutSec = defaultTimeoutSec;
        this.commandTimeoutSec = commandTimeoutSec;
        this.pipeline = pipeline;
        this.projectRoot = projectRoot;
        this.hookEngine = hookEngine;
    }

    public List<ToolResult> execute(List<ToolCall> calls, java.util.function.Consumer<AgentEvent> sink,
                                     AtomicBoolean cancelFlag) {
        List<ToolResult> results = new ArrayList<>();
        List<ToolCall> currentBatch = new ArrayList<>();

        for (ToolCall tc : calls) {
            Tool tool = ToolRegistry.get(tc.name());
            if (tool == null || !tool.isReadOnly()) {
                if (!currentBatch.isEmpty()) {
                    results.addAll(executeConcurrent(currentBatch, sink, cancelFlag));
                    currentBatch.clear();
                }
                results.add(executeOne(tc, cancelFlag));
            } else {
                currentBatch.add(tc);
            }
        }
        if (!currentBatch.isEmpty()) {
            results.addAll(executeConcurrent(currentBatch, sink, cancelFlag));
        }
        return results;
    }

    private List<ToolResult> executeConcurrent(List<ToolCall> batch,
                                                java.util.function.Consumer<AgentEvent> sink,
                                                AtomicBoolean cancelFlag) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ToolResult>> futures = batch.stream()
                .map(tc -> executor.submit(() -> executeOne(tc, cancelFlag)))
                .toList();
            List<ToolResult> results = new ArrayList<>();
            for (Future<ToolResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    results.add(ToolResult.error("EXEC_ERROR", e.getMessage(), ""));
                }
            }
            return results;
        }
    }

    private ToolResult executeOne(ToolCall tc, AtomicBoolean cancelFlag) {
        if (cancelFlag.get()) {
            return ToolResult.cancelled(tc.name());
        }

        // PreToolUse hook
        if (hookEngine != null) {
            var payload = HookPayload.builder(HookEvent.PreToolUse)
                .sessionId("").cwd(projectRoot).mode("default")
                .put("tool_name", tc.name())
                .put("tool_input", tc.parameters())
                .build();
            var intercept = hookEngine.dispatch(HookEvent.PreToolUse, payload, cancelFlag);
            if (intercept.blocked()) {
                return ToolResult.error("HOOK_BLOCKED",
                    "[hook " + intercept.hookName() + "] " + intercept.reason(),
                    "hook intercept");
            }
        }

        Tool tool = ToolRegistry.get(tc.name());
        if (tool == null) {
            return ToolResult.error("TOOL_NOT_FOUND", "工具未注册·" + tc.name(), tc.name());
        }

        ToolCallContext ctx = ToolMetadata.from(tc, projectRoot);
        PermissionOutcome outcome = pipeline.evaluate(ctx, cancelFlag);
        if (outcome.denied()) {
            PermissionDecision.Deny deny = outcome.deny();
            return ToolResult.error(
                "PERMISSION_DENIED",
                deny.reason(),
                "source=" + deny.source() + "; " + deny.suggestion());
        }

        long timeout = "execute_command".equals(tc.name()) ? commandTimeoutSec : defaultTimeoutSec;
        try {
            var result = CompletableFuture.supplyAsync(() -> tool.execute(tc.parameters()))
                .orTimeout(timeout, TimeUnit.SECONDS)
                .exceptionally(ex -> ToolResult.error("TIMEOUT", "超时·" + tc.name(), ex.getMessage()))
                .get();

            // PostToolUse hook (fire and forget)
            if (hookEngine != null) {
                var postPayload = HookPayload.builder(HookEvent.PostToolUse)
                    .sessionId("").cwd(projectRoot).mode("default")
                    .put("tool_name", tc.name())
                    .put("tool_input", tc.parameters())
                    .put("tool_result", result.content() != null
                        ? result.content().substring(0, Math.min(result.content().length(), 500))
                        : "")
                    .put("is_error", !result.success())
                    .build();
                hookEngine.dispatch(HookEvent.PostToolUse, postPayload, cancelFlag);
            }

            return result;
        } catch (Exception e) {
            return ToolResult.error("TOOL_ERROR", e.getMessage() != null ? e.getMessage() : "未知错误", e.getClass().getSimpleName());
        }
    }
}
