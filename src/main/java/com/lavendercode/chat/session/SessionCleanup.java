package com.lavendercode.chat.session;

import com.lavendercode.core.context.SessionIdGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

public final class SessionCleanup {
    private static final Duration DEFAULT_MAX_AGE = Duration.ofDays(30);

    private SessionCleanup() {
    }

    public static void cleanupNow(Path sessionsRoot, Duration maxAge) {
        if (sessionsRoot == null || maxAge == null || !Files.isDirectory(sessionsRoot)) {
            return;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        try (Stream<Path> children = Files.list(sessionsRoot)) {
            for (Path dir : children.toList()) {
                cleanupOne(dir, cutoff);
            }
        } catch (IOException ignored) {
            // Startup cleanup is opportunistic.
        }
    }

    public static Thread startBackground(Path sessionsRoot) {
        return Thread.startVirtualThread(() -> cleanupNow(sessionsRoot, DEFAULT_MAX_AGE));
    }

    private static void cleanupOne(Path dir, Instant cutoff) {
        try {
            if (!Files.isDirectory(dir)) {
                return;
            }
            String id = dir.getFileName().toString();
            if (!SessionIdGenerator.isNewFormat(id)
                || !SessionIdGenerator.timestampInstant(id).isBefore(cutoff)) {
                return;
            }
            deleteRecursively(dir);
        } catch (Exception ignored) {
            // Skip failed directories and keep cleaning the rest.
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
