package com.lavendercode.chat.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SessionTranscriptWriterTest {
    @TempDir Path dir;
    ObjectMapper mapper = new ObjectMapper();

    @Test
    void appendsMessageLineWithRoleContentTs() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
            w.appendMessage(Role.USER, "hi", null, null, "gpt-test");
        }
        String line = Files.readString(file).trim();
        JsonNode n = mapper.readTree(line);
        assertThat(n.get("role").asText()).isEqualTo("user");
        assertThat(n.get("content").asText()).isEqualTo("hi");
        assertThat(n.get("ts").isNumber()).isTrue();
        assertThat(n.get("model").asText()).isEqualTo("gpt-test");
    }

    @Test
    void appendsCompactMarker() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
            w.appendCompactMarker();
        }
        JsonNode n = mapper.readTree(Files.readString(file).trim());
        assertThat(n.get("type").asText()).isEqualTo("compact");
        assertThat(n.has("ts")).isTrue();
    }

    @Test
    void appendsToolMessageWithToolCallId() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        Message toolMsg = Message.toolResult("call-1", ToolResult.success("ok", "body"));
        try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
            w.appendMessage(toolMsg, null);
        }
        JsonNode n = mapper.readTree(Files.readString(file).trim());
        assertThat(n.get("role").asText()).isEqualTo("tool");
        assertThat(n.get("tool_call_id").asText()).isEqualTo("call-1");
    }

    @Test
    void appendIsAdditive() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
            w.appendMessage(Role.USER, "a", null, null, "m");
            w.appendMessage(Role.ASSISTANT, "b", null, null, null);
        }
        assertThat(Files.readAllLines(file)).hasSize(2);
    }
}
