package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;

public sealed interface AgentEvent
    permits AgentEvent.Content, AgentEvent.ToolCallStart, AgentEvent.ToolCallEnd,
            AgentEvent.ToolResultReady, AgentEvent.Usage, AgentEvent.RoundStart,
            AgentEvent.RoundEnd, AgentEvent.Complete, AgentEvent.Stopped, AgentEvent.Error {

    record Content(String text) implements AgentEvent {}
    record ToolCallStart(String toolCallId, String toolName) implements AgentEvent {}
    record ToolCallEnd(ToolCall toolCall) implements AgentEvent {}
    record ToolResultReady(String toolCallId, ToolResult result) implements AgentEvent {}
    record Usage(int inputTokens, int outputTokens) implements AgentEvent {}
    record RoundStart(int round) implements AgentEvent {}
    record RoundEnd(int round) implements AgentEvent {}
    record Complete() implements AgentEvent {}
    record Stopped(StopReason reason, String message) implements AgentEvent {}
    record Error(String message) implements AgentEvent {}

    enum StopReason {
        NATURAL_COMPLETION, MAX_ITERATIONS, USER_CANCELLED, UNKNOWN_TOOLS, STREAM_ERROR
    }
}
