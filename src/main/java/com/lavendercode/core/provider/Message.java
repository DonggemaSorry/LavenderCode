package com.lavendercode.core.provider;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import java.util.List;

public record Message(Role role, String content, List<ToolCall> toolCalls, List<ToolResult> toolResults, String toolCallId) {
    public Message(Role role, String content) {
        this(role, content, List.of(), List.of(), null);
    }

    public static Message assistantWithTools(List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, null, toolCalls, List.of(), null);
    }

    public static Message toolResult(String toolCallId, ToolResult result) {
        return new Message(Role.TOOL, null, List.of(), List.of(result), toolCallId);
    }
}
