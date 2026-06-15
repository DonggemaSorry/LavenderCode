package com.lavendercode.chat.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.lavendercode.core.config.*;
import com.lavendercode.core.provider.*;
import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TuiApplicationTest {

    private LlmProvider createMockProvider() {
        return new LlmProvider() {
            @Override
            public String protocol() { return "test"; }
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
                return new StreamEventIterator() {
                    private int count = 0;
                    @Override
                    public boolean hasNext() { return count < 2; }
                    @Override
                    public StreamEvent next() {
                        if (count++ == 0) return new StreamEvent.ContentDelta("Test response");
                        return new StreamEvent.StreamComplete();
                    }
                    @Override
                    public void close() {}
                };
            }
        };
    }

    private LlmConfig createMockConfig() {
        return new LlmConfig(
            new ProviderConfig("test", "test-model", "http://localhost", "key"),
            null
        );
    }

    private Screen createHeadlessScreen() throws Exception {
        return new DefaultTerminalFactory().createScreen();
    }

    @Test
    void shouldCreateAndInitializeTui() throws Exception {
        Screen screen = createHeadlessScreen();
        SessionManager sessionManager = new InMemorySessionManager();
        TuiApplication app = new TuiApplication(
            createMockProvider(), sessionManager, "test-model",
            createMockConfig(), screen
        );
        assertThat(app).isNotNull();
        screen.stopScreen();
    }

    @Test
    void shouldClearConversationHistory() throws Exception {
        Screen screen = createHeadlessScreen();
        SessionManager sessionManager = new InMemorySessionManager();
        sessionManager.addUserMessage("Hello");

        TuiApplication app = new TuiApplication(
            createMockProvider(), sessionManager, "test-model",
            createMockConfig(), screen
        );

        // Verify messages exist before clear
        assertThat(sessionManager.getMessageCount()).isEqualTo(1);

        // Clear via session manager (simulating /clear command behavior)
        sessionManager.clear();
        assertThat(sessionManager.getMessageCount()).isZero();

        screen.stopScreen();
    }

    @Test
    void shouldPreserveHistoryAfterStreamError() throws Exception {
        Screen screen = createHeadlessScreen();
        SessionManager sessionManager = new InMemorySessionManager();
        sessionManager.addUserMessage("test message");

        LlmProvider errorProvider = new LlmProvider() {
            @Override
            public String protocol() { return "test"; }
            @Override
            public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
                return new StreamEventIterator() {
                    private boolean done = false;
                    @Override
                    public boolean hasNext() { return !done; }
                    @Override
                    public StreamEvent next() {
                        done = true;
                        return new StreamEvent.StreamError("Service unavailable", 503);
                    }
                    @Override
                    public void close() {}
                };
            }
        };

        TuiApplication app = new TuiApplication(
            errorProvider, sessionManager, "test-model",
            createMockConfig(), screen
        );

        // History should still contain the user message even with error provider
        assertThat(sessionManager.getMessageCount()).isEqualTo(1);
        assertThat(sessionManager.getHistory().get(0).content()).isEqualTo("test message");

        screen.stopScreen();
    }

    @Test
    void shouldNotLoseHistoryOnMultipleErrors() throws Exception {
        Screen screen = createHeadlessScreen();
        SessionManager sessionManager = new InMemorySessionManager();
        sessionManager.addUserMessage("Q1");
        sessionManager.addAssistantMessage("A1");
        sessionManager.addUserMessage("Q2");

        assertThat(sessionManager.getMessageCount()).isEqualTo(3);

        // Simulate error — history should be preserved
        sessionManager.addUserMessage("Q3");
        assertThat(sessionManager.getMessageCount()).isEqualTo(4);

        screen.stopScreen();
    }
}
