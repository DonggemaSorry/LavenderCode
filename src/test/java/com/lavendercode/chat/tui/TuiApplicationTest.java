package com.lavendercode.chat.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.lavendercode.core.provider.*;
import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TuiApplicationTest {

    @Test
    void shouldCreateAndInitializeTui() throws Exception {
        Screen screen = new DefaultTerminalFactory()
            .createScreen();
        screen.startScreen();

        SessionManager sessionManager = new InMemorySessionManager();
        LlmProvider mockProvider = new LlmProvider() {
            @Override
            public String protocol() { return "test"; }
            @Override
            public StreamEventIterator streamChat(List<Message> history, com.lavendercode.core.config.LlmConfig config) {
                return new StreamEventIterator() {
                    private boolean done = false;
                    @Override
                    public boolean hasNext() { return !done; }
                    @Override
                    public StreamEvent next() {
                        done = true;
                        return new StreamEvent.StreamComplete();
                    }
                    @Override
                    public void close() {}
                };
            }
        };

        com.lavendercode.core.config.LlmConfig mockConfig = new com.lavendercode.core.config.LlmConfig(
            new com.lavendercode.core.config.ProviderConfig("test", "test-model", "http://localhost", "key"),
            null
        );
        TuiApplication app = new TuiApplication(mockProvider, sessionManager, "test-model", mockConfig, screen);

        assertThat(app).isNotNull();

        screen.stopScreen();
    }
}
