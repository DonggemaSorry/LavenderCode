package com.lavendercode.core.context;

import java.security.SecureRandom;
import java.time.Instant;

public final class SessionIdGenerator {
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionIdGenerator() {}

    public static String generate() {
        long epoch = Instant.now().getEpochSecond();
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return epoch + "-" + suffix;
    }
}
