package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class PersistingSessionManager implements SessionManager {
    private final SessionManager inner;
    private final String modelName;
    private SessionTranscriptWriter writer;
    private boolean persist = true;
    private boolean firstMessage = true;
    private Path projectRoot;
    private String currentSessionId;

    public PersistingSessionManager(SessionManager inner, SessionTranscriptWriter writer,
                                    String modelName, Path projectRoot, String sessionId) {
        this.inner = inner;
        this.writer = writer;
        this.modelName = modelName;
        this.projectRoot = projectRoot;
        this.currentSessionId = sessionId;
    }

    public PersistingSessionManager(SessionManager inner, SessionTranscriptWriter writer, String modelName) {
        this(inner, writer, modelName, null, null);
    }

    public void suspendPersistence() {
        persist = false;
    }

    public void resumePersistence() {
        persist = true;
    }

    public void swapWriter(SessionTranscriptWriter next) throws IOException {
        writer.close();
        writer = next;
        firstMessage = false;
    }

    public void startNewSession(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot;
        this.currentSessionId = generateSessionId();
        var paths = new com.lavendercode.core.context.SessionPaths(projectRoot, currentSessionId);
        paths.ensureDirectories();
        swapWriter(SessionTranscriptWriter.open(paths.conversationJsonl()));
        firstMessage = true;
    }

    public String currentSessionId() {
        return currentSessionId;
    }

    public void close() throws IOException {
        writer.close();
    }

    private static String generateSessionId() {
        var now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + random;
    }

    @Override
    public void addUserMessage(String content) {
        inner.addUserMessage(content);
        if (persist) {
            appendMessage(Role.USER, content);
        }
    }

    @Override
    public void addAssistantMessage(String content) {
        inner.addAssistantMessage(content);
        if (persist) {
            appendMessage(Role.ASSISTANT, content);
        }
    }

    @Override
    public void addToolMessages(List<ToolCall> toolCalls, List<ToolResult> toolResults) {
        inner.addToolMessages(toolCalls, toolResults);
        if (!persist) {
            return;
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            writer.appendMessage(Message.assistantWithTools(toolCalls), modelForNextMessage());
        }
        if (toolResults != null && !toolResults.isEmpty()) {
            for (int i = 0; i < toolResults.size(); i++) {
                String toolCallId = i < toolCalls.size() ? toolCalls.get(i).id() : "unknown";
                writer.appendMessage(Message.toolResult(toolCallId, toolResults.get(i)), modelForNextMessage());
            }
        }
    }

    @Override
    public void removeLastMessages(int count) {
        inner.removeLastMessages(count);
    }

    @Override
    public void replaceHistory(List<Message> messages) {
        inner.replaceHistory(messages);
        if (persist) {
            writer.appendCompactMarker();
            for (Message message : messages) {
                writer.appendMessage(message, null);
            }
            if (!messages.isEmpty()) {
                firstMessage = false;
            }
        }
    }

    @Override
    public void updateToolContent(String toolCallId, String newContent) {
        inner.updateToolContent(toolCallId, newContent);
    }

    @Override
    public List<Message> getHistory() {
        return inner.getHistory();
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @Override
    public int getMessageCount() {
        return inner.getMessageCount();
    }

    private void appendMessage(Role role, String content) {
        writer.appendMessage(role, content, null, null, modelForNextMessage());
    }

    private String modelForNextMessage() {
        if (!firstMessage) {
            return null;
        }
        firstMessage = false;
        return modelName;
    }
}