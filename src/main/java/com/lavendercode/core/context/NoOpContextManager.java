package com.lavendercode.core.context;

import com.lavendercode.chat.terminal.RoundResult;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import java.util.List;
import java.util.function.Consumer;

public final class NoOpContextManager implements ContextManager {
    public static final NoOpContextManager INSTANCE = new NoOpContextManager();

    private NoOpContextManager() {}

    @Override
    public ManageOutcome manageContext(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
        return ManageOutcome.UNCHANGED;
    }

    @Override
    public CompactResult runCompaction(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
        return CompactResult.fail(0, "Context management disabled");
    }

    @Override
    public void onUsage(RoundResult result) { }

    @Override
    public void recordFileReads(List<ToolCall> calls, List<ToolResult> results) { }

    @Override
    public void resetAnchor() { }

    @Override
    public boolean isPromptTooLong(String errorMessage) {
        return PromptTooLongDetector.isPromptTooLong(errorMessage, 0);
    }

    @Override
    public void setEventSink(Consumer<ContextEvent> sink) { }
}
