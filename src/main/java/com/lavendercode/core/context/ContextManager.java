package com.lavendercode.core.context;

import com.lavendercode.chat.terminal.RoundResult;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import java.util.List;
import java.util.function.Consumer;

public interface ContextManager {
    ManageOutcome manageContext(CompactTrigger trigger, List<ToolDefinition> toolDefs);

    CompactResult runCompaction(CompactTrigger trigger, List<ToolDefinition> toolDefs);

    void onUsage(RoundResult result);

    void recordFileReads(List<ToolCall> calls, List<ToolResult> results);

    void resetAnchor();

    boolean isPromptTooLong(String errorMessage);

    void setEventSink(Consumer<ContextEvent> sink);
}
