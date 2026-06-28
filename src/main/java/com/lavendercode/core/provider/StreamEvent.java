package com.lavendercode.core.provider;

import java.util.Map;

public sealed interface StreamEvent
    permits StreamEvent.ContentDelta,
            StreamEvent.ThinkingDelta,
            StreamEvent.ToolCallStart,
            StreamEvent.ToolCallDelta,
            StreamEvent.ToolCallEnd,
            StreamEvent.StreamComplete,
            StreamEvent.StreamError {

    record ContentDelta(String text) implements StreamEvent {}
    record ThinkingDelta(String text) implements StreamEvent {}
    record ToolCallStart(String toolCallId, String toolName) implements StreamEvent {}
    record ToolCallDelta(String toolCallId, String jsonFragment) implements StreamEvent {}
    record ToolCallEnd(String toolCallId, String toolName, Map<String, Object> parameters) implements StreamEvent {}
    record StreamComplete() implements StreamEvent {}
    record StreamError(String message, int statusCode) implements StreamEvent {}
}
