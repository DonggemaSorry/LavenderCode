package com.lavendercode.chat.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCatalogTest {
    @Test
    void listsOnlyNewFormatSessionsWithConversationSortedByMostRecent(@TempDir Path root) throws Exception {
        Path newer = session(root, "20330518-033320-abcd", """
            {"type":"message","ts":"2033-05-18T03:33:20Z","role":"user","content":"This is a very long first request that needs to be truncated for display"}
            {"type":"message","ts":"2033-05-18T03:33:21Z","role":"assistant","model":"claude-test","content":"ok"}
            """, Instant.parse("2033-05-18T03:33:20Z"));
        session(root, "20330518-033319-def4", """
            {"type":"message","ts":"2033-05-18T03:33:19Z","role":"user","content":"Older chat"}
            {"type":"message","ts":"2033-05-18T03:33:20Z","role":"assistant","model":"gpt-test","content":"ok"}
            """, Instant.parse("2033-05-18T03:33:19Z"));
        Files.createDirectories(root.resolve("legacy-session"));
        Files.writeString(root.resolve("legacy-session").resolve("conversation.jsonl"), "{}");
        Files.createDirectories(root.resolve("20330518-033321-ffff"));

        List<SessionListItem> items = SessionCatalog.list(root);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).sessionId()).isEqualTo("20330518-033320-abcd");
        assertThat(items.get(0).title()).isEqualTo("This is a very long first request that needs to be...");
        assertThat(items.get(0).model()).isEqualTo("claude-test");
        assertThat(items.get(0).relativeTime()).isNotBlank();
        assertThat(items.get(0).sizeBytes()).isEqualTo(Files.size(newer));
        assertThat(items.get(0).jsonlPath()).isEqualTo(newer);
        assertThat(items.get(1).sessionId()).isEqualTo("20330518-033319-def4");
    }

    private static Path session(Path root, String id, String jsonl, Instant modifiedAt) throws Exception {
        Path dir = root.resolve(id);
        Files.createDirectories(dir);
        Path conversation = dir.resolve("conversation.jsonl");
        Files.writeString(conversation, jsonl);
        Files.setLastModifiedTime(conversation, FileTime.from(modifiedAt));
        return conversation;
    }
}
