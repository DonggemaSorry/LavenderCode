package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;

import java.util.List;

public interface SessionManager {
    void addUserMessage(String content);
    void addAssistantMessage(String content);
    void addToolMessages(List<ToolCall> toolCalls, List<ToolResult> toolResults);
    /** Removes the last N messages from history (for rolling back failed re-injection). */
    void removeLastMessages(int count);
    void replaceHistory(List<Message> messages);
    void updateToolContent(String toolCallId, String newContent);
    List<Message> getHistory();
    void clear();
    int getMessageCount();
}
