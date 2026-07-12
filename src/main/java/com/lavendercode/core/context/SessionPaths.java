package com.lavendercode.core.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class SessionPaths {
    private final Path sessionRoot;
    private final Path toolResultsDir;

    public SessionPaths(Path projectRoot, String sessionId) {
        this.sessionRoot = projectRoot.resolve(".lavendercode").resolve("sessions").resolve(sessionId);
        this.toolResultsDir = sessionRoot.resolve("tool-results");
    }

    public Path toolResultPath(String toolCallId) {
        return toolResultsDir.resolve(toolCallId);
    }

    public Path toolResultsDir() {
        return toolResultsDir;
    }

    public Path sessionRoot() {
        return sessionRoot;
    }

    public Path conversationJsonl() {
        return sessionRoot.resolve("conversation.jsonl");
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(toolResultsDir);
    }

    public boolean fileExists(String toolCallId) {
        return Files.isRegularFile(toolResultPath(toolCallId));
    }

    public void writeToolResult(String toolCallId, String content) throws IOException {
        Path target = toolResultPath(toolCallId);
        if (Files.exists(target)) return;
        Files.createDirectories(toolResultsDir);
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
