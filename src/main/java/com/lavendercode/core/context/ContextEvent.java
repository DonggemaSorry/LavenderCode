package com.lavendercode.core.context;

public sealed interface ContextEvent {
    record Compacting(String message) implements ContextEvent {}
    record Compacted(int tokensBefore, int tokensAfter) implements ContextEvent {}
    record CompactFailed(String reason) implements ContextEvent {}
}
