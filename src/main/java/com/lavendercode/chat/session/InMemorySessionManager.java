package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;

public class InMemorySessionManager implements SessionManager {

    private final List<Message> messages = new ArrayList<>();

    @Override
    public void addUserMessage(String content) {
        messages.add(new Message(Role.USER, content));
    }

    @Override
    public void addAssistantMessage(String content) {
        messages.add(new Message(Role.ASSISTANT, content));
    }

    @Override
    public void addToolMessages(List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        if (toolCalls != null && !toolCalls.isEmpty()) {
            // Defensive copy: the caller may clear toolCalls immediately after this call
            messages.add(Message.assistantWithTools(new ArrayList<>(toolCalls)));
        }
        if (toolResults != null && !toolResults.isEmpty()) {
            for (int i = 0; i < toolResults.size(); i++) {
                String tcId = i < toolCalls.size() ? toolCalls.get(i).id() : "unknown";
                messages.add(Message.toolResult(tcId, toolResults.get(i)));
            }
        }
    }

    @Override
    public List<Message> getHistory() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    @Override
    public void removeLastMessages(int count) {
        if (count <= 0) return;
        int toRemove = Math.min(count, messages.size());
        for (int i = 0; i < toRemove; i++) {
            messages.remove(messages.size() - 1);
        }
    }

    @Override
    public int getMessageCount() {
        return messages.size();
    }
}
