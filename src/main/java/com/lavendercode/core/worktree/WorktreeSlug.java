package com.lavendercode.core.worktree;

import java.util.regex.Pattern;

public final class WorktreeSlug {
    private static final Pattern SEGMENT = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_LEN = 64;

    private WorktreeSlug() {}

    public static void validate(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Worktree 名称不能为空");
        }
        if (name.length() > MAX_LEN) {
            throw new IllegalArgumentException("Worktree 名称长度不能超过 " + MAX_LEN);
        }
        if (name.startsWith("/") || name.endsWith("/") || name.contains("//")) {
            throw new IllegalArgumentException("Worktree 名称不能以 / 开头或结尾，且不能含连续 //");
        }
        for (String seg : name.split("/", -1)) {
            if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg) || !SEGMENT.matcher(seg).matches()) {
                throw new IllegalArgumentException("非法 Worktree 名称段: " + seg);
            }
        }
    }

    public static String flatten(String name) {
        validate(name);
        return name.replace("/", "+");
    }
}
