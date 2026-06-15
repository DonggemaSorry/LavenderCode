package com.lavendercode.core.provider;

public sealed interface StreamEvent
    permits StreamEvent.ContentDelta,
            StreamEvent.ThinkingDelta,
            StreamEvent.StreamComplete,
            StreamEvent.StreamError {

    record ContentDelta(String text) implements StreamEvent {}
    record ThinkingDelta(String text) implements StreamEvent {}
    record StreamComplete() implements StreamEvent {}
    record StreamError(String message, int statusCode) implements StreamEvent {}
}
