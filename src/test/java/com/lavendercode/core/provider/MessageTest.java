package com.lavendercode.core.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void shouldCreateUserMessage() {
        var msg = new Message(Role.USER, "Hello");
        assertThat(msg.role()).isEqualTo(Role.USER);
        assertThat(msg.content()).isEqualTo("Hello");
    }

    @Test
    void shouldCreateAssistantMessage() {
        var msg = new Message(Role.ASSISTANT, "Hi there!");
        assertThat(msg.role()).isEqualTo(Role.ASSISTANT);
        assertThat(msg.content()).isEqualTo("Hi there!");
    }

    @Test
    void shouldCreateSystemMessage() {
        var msg = new Message(Role.SYSTEM, "You are a helpful assistant.");
        assertThat(msg.role()).isEqualTo(Role.SYSTEM);
        assertThat(msg.content()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void shouldSupportEquality() {
        var msg1 = new Message(Role.USER, "Hello");
        var msg2 = new Message(Role.USER, "Hello");
        var msg3 = new Message(Role.USER, "World");

        assertThat(msg1).isEqualTo(msg2);
        assertThat(msg1).isNotEqualTo(msg3);
        assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode());
    }

    @Test
    void shouldSupportEmptyContent() {
        var msg = new Message(Role.USER, "");
        assertThat(msg.role()).isEqualTo(Role.USER);
        assertThat(msg.content()).isEmpty();
    }

    @Test
    void shouldSupportLongContent() {
        String longText = "A".repeat(10000);
        var msg = new Message(Role.ASSISTANT, longText);
        assertThat(msg.content()).hasSize(10000);
        assertThat(msg.content()).isEqualTo(longText);
    }

    @Test
    void shouldSupportSpecialCharacters() {
        var msg = new Message(Role.USER, "你好，世界！ 🌍 \n\t\b\r");
        assertThat(msg.content()).isEqualTo("你好，世界！ 🌍 \n\t\b\r");
    }
}
