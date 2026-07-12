package com.lavendercode.core.context;

public record ToolResultEntry(int messageIndex, String toolCallId, String content, int utf8Bytes) {}
