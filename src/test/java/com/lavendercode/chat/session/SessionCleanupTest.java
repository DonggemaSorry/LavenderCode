package com.lavendercode.chat.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupTest {
    @Test
    void deletesOnlyExpiredNewFormatSessionDirectories(@TempDir Path root) throws Exception {
        String expired = "20200101-000000-abcd";
        String recent = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-def4";
        Path expiredDir = createSessionDir(root, expired);
        Path recentDir = createSessionDir(root, recent);
        Path legacyDir = createSessionDir(root, "legacy-session");

        SessionCleanup.cleanupNow(root, Duration.ofDays(30));

        assertThat(expiredDir).doesNotExist();
        assertThat(recentDir).exists();
        assertThat(legacyDir).exists();
    }

    @Test
    void cleanupNowIgnoresMissingRoot(@TempDir Path root) {
        Path missing = root.resolve("missing");

        SessionCleanup.cleanupNow(missing, Duration.ofDays(30));

        assertThat(missing).doesNotExist();
    }

    private static Path createSessionDir(Path root, String id) throws Exception {
        Path dir = root.resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("conversation.jsonl"), "{}");
        return dir;
    }
}
