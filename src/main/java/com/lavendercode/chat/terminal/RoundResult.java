package com.lavendercode.chat.terminal;
import com.lavendercode.core.tool.ToolCall;
import java.util.List;

public record RoundResult(String fullText, List<ToolCall> toolCalls,
                          int inputTokens, int outputTokens,
                          int cacheCreationTokens, int cacheReadTokens,
                          String error) {
    public boolean hasError() { return error != null; }
    public boolean noTools() { return toolCalls.isEmpty(); }

    public RoundResult(String fullText, List<ToolCall> toolCalls,
                       int inputTokens, int outputTokens, String error) {
        this(fullText, toolCalls, inputTokens, outputTokens, 0, 0, error);
    }
}
