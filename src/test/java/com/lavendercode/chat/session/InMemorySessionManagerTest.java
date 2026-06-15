package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
    }

    @Test
    void shouldStartWithZeroMessages() {
        assertThat(sessionManager.getMessageCount()).isZero();
        assertThat(sessionManager.getHistory()).isEmpty();
    }

    @Test
    void shouldAddUserMessage() {
        sessionManager.addUserMessage("Hello");

        assertThat(sessionManager.getMessageCount()).isEqualTo(1);
        List<Message> history = sessionManager.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo(Role.USER);
        assertThat(history.get(0).content()).isEqualTo("Hello");
    }

    @Test
    void shouldAddAssistantMessage() {
        sessionManager.addAssistantMessage("Hi there!");

        assertThat(sessionManager.getMessageCount()).isEqualTo(1);
        List<Message> history = sessionManager.getHistory();
        assertThat(history.get(0).role()).isEqualTo(Role.ASSISTANT);
        assertThat(history.get(0).content()).isEqualTo("Hi there!");
    }

    @Test
    void shouldPreserveMessageOrder() {
        sessionManager.addUserMessage("Q1");
        sessionManager.addAssistantMessage("A1");
        sessionManager.addUserMessage("Q2");
        sessionManager.addAssistantMessage("A2");

        List<Message> history = sessionManager.getHistory();
        assertThat(history).hasSize(4);
        assertThat(history.get(0)).isEqualTo(new Message(Role.USER, "Q1"));
        assertThat(history.get(1)).isEqualTo(new Message(Role.ASSISTANT, "A1"));
        assertThat(history.get(2)).isEqualTo(new Message(Role.USER, "Q2"));
        assertThat(history.get(3)).isEqualTo(new Message(Role.ASSISTANT, "A2"));
    }

    @Test
    void shouldClearAllMessages() {
        sessionManager.addUserMessage("Hello");
        sessionManager.addAssistantMessage("Hi");

        sessionManager.clear();

        assertThat(sessionManager.getMessageCount()).isZero();
        assertThat(sessionManager.getHistory()).isEmpty();
    }

    @Test
    void shouldReturnDefensiveCopyOfHistory() {
        sessionManager.addUserMessage("Hello");

        List<Message> history = sessionManager.getHistory();
        history.clear(); // try to mutate returned list

        assertThat(sessionManager.getMessageCount()).isEqualTo(1);
    }
}
