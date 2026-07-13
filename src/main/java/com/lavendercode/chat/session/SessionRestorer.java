package com.lavendercode.chat.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.context.ContextWindowDefaults;
import com.lavendercode.core.context.TokenEstimator;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SessionRestorer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration STALE_AFTER = Duration.ofHours(6);

    private SessionRestorer() {
    }

    public static List<Message> parseMessages(Path jsonl) throws IOException {
        ParsedTranscript transcript = parseTranscript(jsonl);
        return transcript.messages();
    }

    public static RestoreResult restore(Path jsonl,
                                        ContextManager contextManager,
                                        int contextWindow,
                                        TokenEstimator estimator) throws IOException {
        ParsedTranscript transcript = parseTranscript(jsonl);
        List<Message> messages = new ArrayList<>(transcript.messages());
        String reminder = staleReminder(transcript.lastTimestamp(), Instant.now());
        if (reminder != null) {
            messages.add(new Message(Role.USER, reminder));
        }

        boolean compacted = false;
        if (estimator != null
            && contextManager != null
            && estimator.estimateMessages(messages) > ContextWindowDefaults.autoCompactThreshold(contextWindow)) {
            contextManager.runCompaction(CompactTrigger.MANUAL, List.of());
            compacted = true;
        }

        return new RestoreResult(List.copyOf(messages), compacted, reminder);
    }

    public static boolean maybeCompact(SessionManager sessionManager,
                                       ContextManager contextManager,
                                       int contextWindow,
                                       TokenEstimator estimator) {
        if (sessionManager == null || contextManager == null || estimator == null) {
            return false;
        }
        int estimate = estimator.estimateMessages(sessionManager.getHistory());
        if (estimate > ContextWindowDefaults.autoCompactThreshold(contextWindow)) {
            contextManager.runCompaction(CompactTrigger.MANUAL, List.of());
            return true;
        }
        return false;
    }

    private static ParsedTranscript parseTranscript(Path jsonl) throws IOException {
        if (jsonl == null || !Files.isRegularFile(jsonl)) {
            return new ParsedTranscript(List.of(), null);
        }

        List<JsonNode> nodes = new ArrayList<>();
        int start = 0;
        for (String line : Files.readAllLines(jsonl)) {
            JsonNode node = parse(line);
            nodes.add(node);
            if (node != null && "compact".equals(node.path("type").asText(null))) {
                start = nodes.size();
            }
        }

        List<Message> messages = new ArrayList<>();
        Instant lastTimestamp = null;
        for (int i = start; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            if (node == null) {
                continue;
            }
            Message message = toMessage(node);
            if (message == null) {
                continue;
            }
            messages.add(message);
            Instant ts = timestamp(node);
            if (ts != null) {
                lastTimestamp = ts;
            }
        }
        dropTrailingOrphanAssistantToolCall(messages);
        return new ParsedTranscript(List.copyOf(messages), lastTimestamp);
    }

    private static JsonNode parse(String line) {
        try {
            return MAPPER.readTree(line);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Message toMessage(JsonNode node) {
        String roleName = node.path("role").asText(null);
        if (roleName == null) {
            return null;
        }

        Role role;
        try {
            role = Role.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return switch (role) {
            case USER, ASSISTANT -> new Message(
                role,
                content(node),
                toolCalls(node.path("tool_calls")),
                List.of(),
                null
            );
            case TOOL -> Message.toolResult(
                node.path("tool_call_id").asText(null),
                firstToolResult(node)
            );
            case SYSTEM -> new Message(Role.SYSTEM, content(node));
        };
    }

    private static String content(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return null;
        }
        return content.isTextual() ? content.asText() : content.toString();
    }

    private static List<ToolCall> toolCalls(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText(null);
            String name = item.path("name").asText(null);
            Map<String, Object> parameters = MAPPER.convertValue(
                item.path("parameters"),
                new TypeReference<>() {
                }
            );
            calls.add(new ToolCall(id, name, parameters == null ? Map.of() : parameters));
        }
        return List.copyOf(calls);
    }

    private static ToolResult firstToolResult(JsonNode node) {
        JsonNode results = node.path("tool_results");
        if (results.isArray() && !results.isEmpty()) {
            return toToolResult(results.get(0));
        }
        if (node.has("tool_result")) {
            return toToolResult(node.get("tool_result"));
        }
        return ToolResult.success(summary(node), content(node));
    }

    private static ToolResult toToolResult(JsonNode node) {
        boolean success = !node.has("success") || node.path("success").asBoolean(true);
        String summary = node.path("summary").asText(success ? "ok" : "error");
        String content = node.hasNonNull("content") ? node.get("content").asText() : null;
        if (success) {
            return ToolResult.success(summary, content);
        }
        return ToolResult.error(
            node.path("errorCategory").asText(node.path("error_category").asText("ERROR")),
            summary,
            node.path("errorDetail").asText(node.path("error_detail").asText(null))
        );
    }

    private static String summary(JsonNode node) {
        return node.hasNonNull("summary") ? node.get("summary").asText() : "tool result";
    }

    private static Instant timestamp(JsonNode node) {
        if (!node.hasNonNull("ts")) {
            return null;
        }
        try {
            return Instant.parse(node.get("ts").asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void dropTrailingOrphanAssistantToolCall(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        Message last = messages.get(messages.size() - 1);
        if (last.role() == Role.ASSISTANT
            && last.toolCalls() != null
            && !last.toolCalls().isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    private static String staleReminder(Instant lastTimestamp, Instant now) {
        if (lastTimestamp == null) {
            return null;
        }
        Duration paused = Duration.between(lastTimestamp, now);
        if (paused.compareTo(STALE_AFTER) <= 0) {
            return null;
        }
        return "[系统提示] 本会话已暂停 " + durationText(paused)
            + "。部分上下文可能已过时，如需最新信息请重新读取相关文件。";
    }

    private static String durationText(Duration duration) {
        long days = duration.toDays();
        if (days > 0) {
            return days + " 天";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + " 小时";
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes + " 分钟";
    }

    private record ParsedTranscript(List<Message> messages, Instant lastTimestamp) {
    }
}
