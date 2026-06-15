package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;

import java.util.List;

public interface SessionManager {
    void addUserMessage(String content);
    void addAssistantMessage(String content);
    List<Message> getHistory();
    void clear();
    int getMessageCount();
}
