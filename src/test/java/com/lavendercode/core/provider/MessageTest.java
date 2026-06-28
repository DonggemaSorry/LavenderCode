package com.lavendercode.core.provider;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

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

    @Test
    void backwardCompat() {
        Message m = new Message(Role.USER, "hello");
        assertThat(m.content()).isEqualTo("hello");
        assertThat(m.toolCalls()).isEmpty();
        assertThat(m.toolResults()).isEmpty();
        assertThat(m.toolCallId()).isNull();
    }

    @Test
    void assistantWithTools() {
        var tc = new ToolCall("id", "read", Map.of());
        var m = Message.assistantWithTools(List.of(tc));
        assertThat(m.role()).isEqualTo(Role.ASSISTANT);
        assertThat(m.toolCalls()).contains(tc);
        assertThat(m.content()).isNull();
    }

    @Test
    void toolResult() {
        var tr = ToolResult.success("ok", "c");
        var m = Message.toolResult("cid", tr);
        assertThat(m.role()).isEqualTo(Role.TOOL);
        assertThat(m.toolCallId()).isEqualTo("cid");
        assertThat(m.toolResults()).contains(tr);
    }
}
