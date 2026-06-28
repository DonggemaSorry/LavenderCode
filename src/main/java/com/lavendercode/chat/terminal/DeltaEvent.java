package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.ToolCall;
import java.util.Objects;

public sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.ToolCallStart,
            DeltaEvent.ToolCallDelta,
            DeltaEvent.ToolCallEnd,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {

    record Content(String text) implements DeltaEvent {
        public Content { Objects.requireNonNull(text); }
    }

    record ToolCallStart(String toolCallId, String toolName) implements DeltaEvent {}
    record ToolCallDelta(String toolCallId, String jsonFragment) implements DeltaEvent {}
    record ToolCallEnd(ToolCall toolCall) implements DeltaEvent {}

    record Complete() implements DeltaEvent {}

    record Error(String message, int statusCode) implements DeltaEvent {
        public Error { Objects.requireNonNull(message); }
    }

    record Usage(int inputTokens, int outputTokens) implements DeltaEvent {}
}
