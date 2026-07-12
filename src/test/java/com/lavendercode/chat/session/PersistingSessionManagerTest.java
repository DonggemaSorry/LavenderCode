package com.lavendercode.chat.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistingSessionManagerTest {
    @TempDir Path dir;
    ObjectMapper mapper = new ObjectMapper();
    List<SessionTranscriptWriter> writers = new ArrayList<>();

    @AfterEach
    void closeWriters() throws Exception {
        for (SessionTranscriptWriter writer : writers) {
            writer.close();
        }
    }

    @Test
    void addUserAndAssistantPersistTwoLines() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        SessionManager manager = manager(file);

        manager.addUserMessage("hi");
        manager.addAssistantMessage("hello");

        List<JsonNode> lines = jsonLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("role").asText()).isEqualTo("user");
        assertThat(lines.get(0).get("content").asText()).isEqualTo("hi");
        assertThat(lines.get(0).get("model").asText()).isEqualTo("gpt-x");
        assertThat(lines.get(1).get("role").asText()).isEqualTo("assistant");
        assertThat(lines.get(1).get("content").asText()).isEqualTo("hello");
        assertThat(lines.get(1).has("model")).isFalse();
    }

    @Test
    void replaceHistoryWritesCompactThenMessages() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        SessionManager manager = manager(file);

        manager.replaceHistory(List.of(
            new Message(Role.USER, "kept question"),
            new Message(Role.ASSISTANT, "kept answer")
        ));

        List<JsonNode> lines = jsonLines(file);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).get("type").asText()).isEqualTo("compact");
        assertThat(lines.get(1).get("role").asText()).isEqualTo("user");
        assertThat(lines.get(1).get("content").asText()).isEqualTo("kept question");
        assertThat(lines.get(1).has("model")).isFalse();
        assertThat(lines.get(2).get("role").asText()).isEqualTo("assistant");
        assertThat(lines.get(2).get("content").asText()).isEqualTo("kept answer");
        assertThat(lines.get(2).has("model")).isFalse();
    }

    @Test
    void addToolMessagesPersistsAssistantToolCallsAndToolCallId() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        SessionManager manager = manager(file);

        manager.addToolMessages(
            List.of(new ToolCall("call-1", "read_file", Map.of("path", "README.md"))),
            List.of(ToolResult.success("read", "content"))
        );

        List<JsonNode> lines = jsonLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("role").asText()).isEqualTo("assistant");
        assertThat(lines.get(0).get("tool_calls")).hasSize(1);
        assertThat(lines.get(0).get("tool_calls").get(0).get("id").asText()).isEqualTo("call-1");
        assertThat(lines.get(1).get("role").asText()).isEqualTo("tool");
        assertThat(lines.get(1).get("tool_call_id").asText()).isEqualTo("call-1");
        assertThat(lines.get(1).get("tool_results")).hasSize(1);
        assertThat(lines.get(1).get("tool_results").get(0).get("content").asText()).isEqualTo("content");
    }

    @Test
    void suspendPersistenceSkipsDiskOnReplace() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        PersistingSessionManager manager = manager(file);

        manager.addUserMessage("before suspend");
        manager.suspendPersistence();
        manager.replaceHistory(List.of(new Message(Role.USER, "not persisted")));

        String content = Files.readString(file);
        assertThat(content).contains("before suspend");
        assertThat(content).doesNotContain("not persisted");
        assertThat(jsonLines(file)).hasSize(1);
    }

    @Test
    void clearOnlyClearsMemory() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        PersistingSessionManager manager = manager(file);

        manager.addUserMessage("still on disk");
        manager.clear();

        assertThat(manager.getMessageCount()).isZero();
        assertThat(Files.readString(file)).contains("still on disk");
    }

    @Test
    void updateToolContentDoesNotAddLines() throws Exception {
        Path file = dir.resolve("conversation.jsonl");
        PersistingSessionManager manager = manager(file);

        manager.replaceHistory(List.of(Message.toolResult("call-1", ToolResult.success("ok", "old"))));
        int lineCount = jsonLines(file).size();
        manager.updateToolContent("call-1", "new");

        assertThat(jsonLines(file)).hasSize(lineCount);
        assertThat(manager.getHistory().get(0).toolResults().get(0).content()).isEqualTo("new");
    }

    private PersistingSessionManager manager(Path file) throws Exception {
        SessionTranscriptWriter writer = SessionTranscriptWriter.open(file);
        writers.add(writer);
        return new PersistingSessionManager(
            new InMemorySessionManager(),
            writer,
            "gpt-x"
        );
    }

    private List<JsonNode> jsonLines(Path file) throws Exception {
        return Files.readAllLines(file).stream()
            .map(this::readJson)
            .toList();
    }

    private JsonNode readJson(String line) {
        try {
            return mapper.readTree(line);
        } catch (Exception e) {
            throw new AssertionError("Invalid JSONL line: " + line, e);
        }
    }
}
