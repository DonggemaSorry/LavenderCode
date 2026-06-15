package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;

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
    public List<Message> getHistory() {
        return new ArrayList<>(messages);
    }

    @Override
    public void clear() {
        messages.clear();
    }

    @Override
    public int getMessageCount() {
        return messages.size();
    }
}
