package com.lavendercode.core.context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SessionIdGenerator {
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionIdGenerator() {}

    public static String generate() {
        String stamp = LocalDateTime.now().format(FMT);
        return stamp + "-" + randomHex4();
    }

    static String randomHex4() {
        int v = RANDOM.nextInt(0x10000);
        return String.format("%04x", v);
    }

    /** 新格式可解析；旧格式返回 false */
    public static boolean isNewFormat(String sessionId) {
        return sessionId != null && sessionId.matches("\\d{8}-\\d{6}-[0-9a-f]{4}");
    }
}
