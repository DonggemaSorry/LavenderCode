package com.lavendercode.core.context;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SessionIdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Pattern NEW_FORMAT =
        Pattern.compile("\\d{8}-\\d{6}-[0-9a-f]{4}");

    private SessionIdGenerator() {
    }

    public static String generate() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String suffix = "%04x".formatted(RANDOM.nextInt(0x10000));
        return timestamp + "-" + suffix;
    }

    public static boolean isNewFormat(String sessionId) {
        return sessionId != null && NEW_FORMAT.matcher(sessionId).matches();
    }

    public static Instant timestampInstant(String sessionId) {
        if (!isNewFormat(sessionId)) {
            throw new IllegalArgumentException("Not a new-format session id: " + sessionId);
        }
        LocalDateTime value = LocalDateTime.parse(sessionId.substring(0, 15), FORMATTER);
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
