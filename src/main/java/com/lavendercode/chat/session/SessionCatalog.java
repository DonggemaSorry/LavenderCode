package com.lavendercode.chat.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.context.SessionIdGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class SessionCatalog {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONVERSATION_FILE = "conversation.jsonl";

    private SessionCatalog() {
    }

    public static List<SessionListItem> list(Path sessionsRoot) throws IOException {
        if (sessionsRoot == null || !Files.isDirectory(sessionsRoot)) {
            return List.of();
        }

        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(sessionsRoot)) {
            for (Path dir : children.toList()) {
                String sessionId = dir.getFileName().toString();
                Path jsonl = dir.resolve(CONVERSATION_FILE);
                if (!Files.isDirectory(dir)
                    || !SessionIdGenerator.isNewFormat(sessionId)
                    || !Files.isRegularFile(jsonl)) {
                    continue;
                }
                Instant modifiedAt = Files.getLastModifiedTime(jsonl).toInstant();
                entries.add(new Entry(modifiedAt, toItem(sessionId, jsonl, modifiedAt)));
            }
        }

        entries.sort(Comparator.comparing(Entry::modifiedAt).reversed());
        return entries.stream().map(Entry::item).toList();
    }

    private static SessionListItem toItem(String sessionId, Path jsonl, Instant modifiedAt) throws IOException {
        Metadata metadata = readMetadata(jsonl);
        return new SessionListItem(
            sessionId,
            truncateTitle(metadata.title()),
            RelativeTime.format(modifiedAt, Instant.now()),
            metadata.model(),
            Files.size(jsonl),
            jsonl
        );
    }

    private static Metadata readMetadata(Path jsonl) throws IOException {
        String title = "Untitled session";
        String model = "";
        boolean foundTitle = false;
        boolean foundModel = false;

        for (String line : Files.readAllLines(jsonl)) {
            JsonNode node = parse(line);
            if (node == null) {
                continue;
            }
            if (!foundTitle && "user".equals(node.path("role").asText(null)) && node.hasNonNull("content")) {
                title = node.get("content").asText();
                foundTitle = true;
            }
            if (!foundModel && node.hasNonNull("model")) {
                model = node.get("model").asText();
                foundModel = true;
            }
            if (foundTitle && foundModel) {
                break;
            }
        }
        return new Metadata(title, model);
    }

    private static JsonNode parse(String line) {
        try {
            return MAPPER.readTree(line);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String truncateTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled session";
        }
        return title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }

    private record Entry(Instant modifiedAt, SessionListItem item) {
    }

    private record Metadata(String title, String model) {
    }
}
