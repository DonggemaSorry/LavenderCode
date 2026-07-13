package com.lavendercode.chat.session;

import com.lavendercode.chat.terminal.RoundResult;
import com.lavendercode.core.context.*;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRestorerTest {
    @Test
    void parsesAfterLastCompactMarkerAndSkipsBadJson(@TempDir Path dir) throws Exception {
        Path jsonl = write(dir, """
            {"type":"message","role":"user","content":"before"}
            not-json
            {"type":"compact","ts":"2026-07-13T00:00:00Z"}
            {"type":"message","role":"user","content":"after"}
            """);

        List<Message> messages = SessionRestorer.parseMessages(jsonl);

        assertThat(messages).extracting(Message::content).containsExactly("after");
    }

    @Test
    void reconstructsToolCallsAndToolResults(@TempDir Path dir) throws Exception {
        Path jsonl = write(dir, """
            {"type":"message","role":"assistant","content":"checking","tool_calls":[{"id":"call-1","name":"read_file","parameters":{"path":"pom.xml"}}]}
            {"type":"message","role":"tool","tool_call_id":"call-1","tool_results":[{"success":true,"summary":"ok","content":"file body"}]}
            """);

        List<Message> messages = SessionRestorer.parseMessages(jsonl);

        assertThat(messages).hasSize(2);
        ToolCall call = messages.get(0).toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.name()).isEqualTo("read_file");
        assertThat(call.parameters()).containsEntry("path", "pom.xml");
        assertThat(messages.get(1).role()).isEqualTo(Role.TOOL);
        assertThat(messages.get(1).toolCallId()).isEqualTo("call-1");
        assertThat(messages.get(1).toolResults().get(0).content()).isEqualTo("file body");
    }

    @Test
    void truncatesTrailingAssistantToolCallWithoutToolResult(@TempDir Path dir) throws Exception {
        Path jsonl = write(dir, """
            {"type":"message","role":"user","content":"please read"}
            {"type":"message","role":"assistant","tool_calls":[{"id":"call-1","name":"read_file","parameters":{"path":"pom.xml"}}]}
            """);

        List<Message> messages = SessionRestorer.parseMessages(jsonl);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo(Role.USER);
    }

    @Test
    void restoreReturnsAndAppendsStaleSessionReminder(@TempDir Path dir) throws Exception {
        Path jsonl = write(dir, """
            {"type":"message","ts":"2026-07-12T00:00:00Z","role":"user","content":"old question"}
            """);

        RestoreResult result = SessionRestorer.restore(jsonl, new RecordingContextManager(), 128_000, new TokenEstimator());

        assertThat(result.timeSpanReminderOrNull()).contains("[系统提示] 本会话已暂停");
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(1).role()).isEqualTo(Role.USER);
        assertThat(result.messages().get(1).content()).isEqualTo(result.timeSpanReminderOrNull());
    }

    @Test
    void readsWriterNumericTimestampAsLastTimestamp(@TempDir Path dir) throws Exception {
        Path jsonl = dir.resolve("conversation.jsonl");
        try (SessionTranscriptWriter writer = SessionTranscriptWriter.open(jsonl)) {
            writer.appendMessage(Role.USER, "round trip", null, null, "test-model");
        }

        Instant lastTimestamp = lastTimestamp(jsonl);

        assertThat(lastTimestamp).isNotNull();
        assertThat(SessionRestorer.parseMessages(jsonl))
            .extracting(Message::content)
            .containsExactly("round trip");
    }

    @Test
    void numericOldTimestampProducesStaleSessionReminder(@TempDir Path dir) throws Exception {
        long oldEpochSeconds = Instant.now().minusSeconds(7 * 60 * 60).getEpochSecond();
        Path jsonl = write(dir, """
            {"type":"message","ts":%d,"role":"user","content":"old numeric question"}
            """.formatted(oldEpochSeconds));

        RestoreResult result = SessionRestorer.restore(jsonl, new RecordingContextManager(), 128_000, new TokenEstimator());

        assertThat(result.timeSpanReminderOrNull()).contains("[系统提示] 本会话已暂停");
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).content()).isEqualTo("old numeric question");
        assertThat(result.messages().get(1).content()).isEqualTo(result.timeSpanReminderOrNull());
    }

    @Test
    void maybeCompactRunsManualCompactionWhenEstimateExceedsThreshold() {
        SessionManager sessionManager = new InMemorySessionManager();
        sessionManager.addUserMessage("large enough");
        RecordingContextManager contextManager = new RecordingContextManager();

        boolean compacted = SessionRestorer.maybeCompact(sessionManager, contextManager, 1, new TokenEstimator());

        assertThat(compacted).isTrue();
        assertThat(contextManager.compactions).isEqualTo(1);
    }

    private static Path write(Path dir, String content) throws Exception {
        Path jsonl = dir.resolve("conversation.jsonl");
        Files.writeString(jsonl, content);
        return jsonl;
    }

    private static Instant lastTimestamp(Path jsonl) throws Exception {
        Method parseTranscript = SessionRestorer.class.getDeclaredMethod("parseTranscript", Path.class);
        parseTranscript.setAccessible(true);
        Object transcript = parseTranscript.invoke(null, jsonl);
        Method lastTimestamp = transcript.getClass().getDeclaredMethod("lastTimestamp");
        lastTimestamp.setAccessible(true);
        return (Instant) lastTimestamp.invoke(transcript);
    }

    private static final class RecordingContextManager implements ContextManager {
        int compactions;

        @Override
        public ManageOutcome manageContext(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
            return ManageOutcome.UNCHANGED;
        }

        @Override
        public CompactResult runCompaction(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
            compactions++;
            return CompactResult.ok(100, 10);
        }

        @Override
        public void onUsage(RoundResult result) {
        }

        @Override
        public void recordFileReads(List<ToolCall> calls, List<ToolResult> results) {
        }

        @Override
        public void resetAnchor() {
        }

        @Override
        public boolean isPromptTooLong(String errorMessage) {
            return false;
        }

        @Override
        public void setEventSink(Consumer<ContextEvent> sink) {
        }
    }
}
