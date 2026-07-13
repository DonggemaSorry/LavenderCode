package com.lavendercode.chat.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PersistingSessionManagerNewSessionTest {

    @TempDir
    Path tempDir;

    private PersistingSessionManager manager;

    @AfterEach
    void closeManager() throws Exception {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void startNewSessionCreatesNewJsonlFile() throws Exception {
        var inner = new InMemorySessionManager();
        var sessionsDir = tempDir.resolve(".lavendercode/sessions");
        Files.createDirectories(sessionsDir);

        String oldSessionId = "20260713-100000-aaaa1111";
        var oldPaths = new com.lavendercode.core.context.SessionPaths(tempDir, oldSessionId);
        oldPaths.ensureDirectories();
        var oldWriter = SessionTranscriptWriter.open(oldPaths.conversationJsonl());

        manager = new PersistingSessionManager(inner, oldWriter, "test-model");
        manager.addUserMessage("hello old session");

        manager.startNewSession(tempDir);

        // Old session file should exist
        assertThat(Files.exists(oldPaths.conversationJsonl())).isTrue();

        // New session ID should differ
        String newId = manager.currentSessionId();
        assertThat(newId).isNotEqualTo(oldSessionId);

        // New JSONL file should exist
        var newPaths = new com.lavendercode.core.context.SessionPaths(tempDir, newId);
        assertThat(Files.exists(newPaths.conversationJsonl())).isTrue();
    }
}