package com.lavendercode.core.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.prompt.PromptContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryService {
    private static final int MAX_INDEX_CHARS = 25 * 1024;
    private static final String TRUNCATED_MARKER = "\n(index truncated)";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(MemoryService.class.getName());

    private final Path projectMemoryDir;
    private final Path userMemoryDir;
    private final ReentrantLock updateLock = new ReentrantLock();
    private volatile String indexCache = "";

    public MemoryService(Path projectRoot, Path userHome) {
        this.projectMemoryDir = projectRoot.resolve(".lavendercode/memory");
        this.userMemoryDir = userHome.resolve(".lavendercode/memory");
    }

    public String loadIndex() throws IOException {
        List<String> indexes = new ArrayList<>();
        readIndex(projectMemoryDir).ifPresent(indexes::add);
        readIndex(userMemoryDir).ifPresent(indexes::add);

        String index = truncate(String.join("\n", indexes));
        indexCache = index;
        return index;
    }

    public String currentIndex() {
        return indexCache;
    }

    public List<MemoryAction> parseActions(String json) throws IOException {
        String array = extractJsonArray(stripMarkdownFence(json == null ? "" : json.trim()));
        if (array.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(array, new TypeReference<>() {});
    }

    public void applyActions(List<MemoryAction> actions) throws IOException {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        updateLock.lock();
        try {
            for (MemoryAction action : actions) {
                applyAction(action);
            }
        } finally {
            updateLock.unlock();
        }
    }

    public boolean shouldUpdate(int turnCount, String lastUserMessage) {
        if (turnCount > 0 && turnCount % 5 == 0) {
            return true;
        }
        String message = lastUserMessage == null ? "" : lastUserMessage.toLowerCase(Locale.ROOT);
        return message.contains("记住")
            || message.contains("记忆")
            || message.contains("别忘")
            || message.contains("remember")
            || message.contains("memo");
    }

    public void maybeUpdateAsync(LlmProvider provider, LlmConfig config,
                                 List<Message> recentMessages,
                                 int turnCount,
                                 String lastUserMessage) {
        if (!shouldUpdate(turnCount, lastUserMessage)) {
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                String response = requestMemoryActions(provider, config, recentMessages);
                List<MemoryAction> actions = parseActions(response);
                applyActions(actions);
                loadIndex();
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Memory update failed: " + e.getMessage(), e);
                // Memory updates must never affect the foreground chat turn.
            }
        });
    }

    private String requestMemoryActions(LlmProvider provider, LlmConfig config,
                                        List<Message> recentMessages) throws Exception {
        String prompt = buildUpdatePrompt(recentMessages == null ? List.of() : recentMessages);
        PromptContext promptContext = new PromptContext(prompt, "", List.of());
        StringBuilder response = new StringBuilder();
        try (StreamEventIterator events = provider.streamChat(
            List.of(new Message(Role.USER, prompt)), config, List.of(), promptContext)) {
            while (events.hasNext()) {
                StreamEvent event = events.next();
                switch (event) {
                    case StreamEvent.ContentDelta c -> response.append(c.text());
                    case StreamEvent.StreamError e -> throw new IOException(e.message());
                    default -> {
                    }
                }
            }
        }
        return response.toString();
    }

    private String buildUpdatePrompt(List<Message> recentMessages) {
        StringBuilder prompt = new StringBuilder("""
            You update LavenderCode long-term memory.
            Return JSON array only. No markdown, no prose.

            Current memory index:
            """);
        prompt.append(currentIndex().isBlank() ? "(empty)" : currentIndex());
        prompt.append("\n\nRecent messages:\n");
        for (Message message : recentMessages) {
            if (message.content() != null && !message.content().isBlank()) {
                prompt.append("- ")
                    .append(message.role())
                    .append(": ")
                    .append(message.content().replace("\n", "\\n"))
                    .append('\n');
            }
        }
        prompt.append("""

            Emit actions with fields:
            action (create|update|delete), level (project|user), type, title, slug, filename, content.
            Use [] when no memory update is needed.
            """);
        return prompt.toString();
    }

    private void applyAction(MemoryAction action) throws IOException {
        if (action == null || action.action() == null || action.type() == null) {
            return;
        }
        Path memoryDir = memoryDir(action.level());
        Files.createDirectories(memoryDir);

        String normalized = action.action().toLowerCase(Locale.ROOT);
        Path note = memoryDir.resolve(noteFileName(action));
        if ("delete".equals(normalized)) {
            Files.deleteIfExists(note);
            upsertIndexLine(memoryDir, action, null);
            return;
        }

        if ("create".equals(normalized) || "update".equals(normalized)) {
            String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String created = "update".equals(normalized)
                ? frontmatterValue(note, "created").orElse(now)
                : now;
            Files.writeString(note, renderNote(action, created, now));
            upsertIndexLine(memoryDir, action, indexLine(action));
        }
    }

    private Path memoryDir(String level) {
        return "user".equalsIgnoreCase(level) ? userMemoryDir : projectMemoryDir;
    }

    private static String noteFileName(MemoryAction action) {
        return action.type().name() + "_" + action.slug() + ".md";
    }

    private static String renderNote(MemoryAction action, String created, String updated) {
        return """
            ---
            level: %s
            type: %s
            title: %s
            slug: %s
            created: %s
            updated: %s
            ---

            %s
            """.formatted(
            value(action.level()),
            action.type().name(),
            value(action.title()),
            value(action.slug()),
            value(created),
            value(updated),
            value(action.content()));
    }

    private static Optional<String> frontmatterValue(Path note, String key) {
        if (!Files.isRegularFile(note)) {
            return Optional.empty();
        }
        try {
            return Files.readAllLines(note).stream()
                .filter(line -> line.startsWith(key + ": "))
                .map(line -> line.substring((key + ": ").length()).trim())
                .filter(value -> !value.isBlank())
                .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private void upsertIndexLine(Path memoryDir, MemoryAction action, String replacement) throws IOException {
        Path index = memoryDir.resolve("MEMORY.md");
        List<String> lines = Files.exists(index)
            ? new ArrayList<>(Files.readAllLines(index))
            : new ArrayList<>();
        String prefix = "- [" + action.type().name() + "] " + value(action.title()) + " ";
        lines.removeIf(line -> line.startsWith(prefix));
        if (replacement != null) {
            lines.add(replacement);
        }
        Files.writeString(index, String.join("\n", lines));
    }

    private static String indexLine(MemoryAction action) {
        return "- [" + action.type().name() + "] "
            + value(action.title())
            + " — "
            + summary(action.content());
    }

    private static String summary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String firstLine = content.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse("");
        return firstLine.length() <= 160 ? firstLine : firstLine.substring(0, 160);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String stripMarkdownFence(String json) {
        if (!json.startsWith("```")) {
            return json;
        }
        int firstNewline = json.indexOf('\n');
        int lastFence = json.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return json;
        }
        return json.substring(firstNewline + 1, lastFence).trim();
    }

    private static String extractJsonArray(String json) {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < start) {
            return "";
        }
        return json.substring(start, end + 1);
    }

    private static Optional<String> readIndex(Path memoryDir) throws IOException {
        Path index = memoryDir.resolve("MEMORY.md");
        if (!Files.exists(index)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(index));
    }

    private static String truncate(String index) {
        if (index.length() <= MAX_INDEX_CHARS) {
            return index;
        }
        return index.substring(0, MAX_INDEX_CHARS) + TRUNCATED_MARKER;
    }
}
