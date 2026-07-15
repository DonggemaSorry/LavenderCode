package com.lavendercode.core.team;

import java.util.Set;

public final class TeamName {
    private TeamName() {}

    public static String sanitize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("团队名不能为空");
        }
        String s = raw.trim().replaceAll("[^a-zA-Z0-9._-]+", "-")
            .replaceAll("^-+", "").replaceAll("-+$", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("团队名净化后为空");
        }
        return s;
    }

    public static String ensureUnique(String sanitized, Set<String> existing) {
        if (!existing.contains(sanitized)) {
            return sanitized;
        }
        for (int i = 2; i < 10_000; i++) {
            String cand = sanitized + "-" + i;
            if (!existing.contains(cand)) {
                return cand;
            }
        }
        throw new IllegalStateException("无法生成唯一团队名: " + sanitized);
    }
}
