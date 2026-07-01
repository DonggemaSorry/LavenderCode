package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchingToolExecutor {
    private final long defaultTimeoutSec;
    private final long commandTimeoutSec;

    public BatchingToolExecutor(long defaultTimeoutSec, long commandTimeoutSec) {
        this.defaultTimeoutSec = defaultTimeoutSec;
        this.commandTimeoutSec = commandTimeoutSec;
    }

    public List<ToolResult> execute(List<ToolCall> calls, java.util.function.Consumer<AgentEvent> sink,
                                     AtomicBoolean cancelFlag) {
        List<ToolResult> results = new ArrayList<>();
        List<ToolCall> currentBatch = new ArrayList<>();

        for (ToolCall tc : calls) {
            Tool tool = ToolRegistry.get(tc.name());
            if (tool == null || !tool.isReadOnly()) {
                // Flush concurrent batch first
                if (!currentBatch.isEmpty()) {
                    results.addAll(executeConcurrent(currentBatch, sink, cancelFlag));
                    currentBatch.clear();
                }
                // Execute serially (unknown or side-effect)
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
        if (cancelFlag.get()) return ToolResult.cancelled(tc.name());
        Tool tool = ToolRegistry.get(tc.name());
        if (tool == null) return ToolResult.error("TOOL_NOT_FOUND", "工具未注册·" + tc.name(), tc.name());
        long timeout = "execute_command".equals(tc.name()) ? commandTimeoutSec : defaultTimeoutSec;
        try {
            return CompletableFuture.supplyAsync(() -> tool.execute(tc.parameters()))
                .orTimeout(timeout, TimeUnit.SECONDS)
                .exceptionally(ex -> ToolResult.error("TIMEOUT", "超时·" + tc.name(), ex.getMessage()))
                .get();
        } catch (Exception e) {
            return ToolResult.error("TOOL_ERROR", e.getMessage() != null ? e.getMessage() : "未知错误", e.getClass().getSimpleName());
        }
    }
}
