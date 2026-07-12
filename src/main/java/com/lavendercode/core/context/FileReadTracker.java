package com.lavendercode.core.context;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public final class FileReadTracker {
    private static final Pattern LINE_PREFIX = Pattern.compile("(?m)^\\s*\\d+\\|");

    private final Deque<FileSnapshot> snapshots = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void record(List<ToolCall> calls, List<ToolResult> results) {
        lock.lock();
        try {
            for (int i = 0; i < calls.size(); i++) {
                ToolCall call = calls.get(i);
                if (!ContextConstants.READ_FILE_TOOL_NAME.equals(call.name())) continue;
                if (i >= results.size()) continue;
                ToolResult r = results.get(i);
                if (!r.success() || r.content() == null) continue;
                String path = extractPath(call.parameters());
                if (path == null) continue;
                String pure = stripLinePrefixes(r.content());
                snapshots.removeIf(s -> s.path().equals(path));
                snapshots.addFirst(new FileSnapshot(path, Instant.now(), pure));
                while (snapshots.size() > ContextConstants.MAX_FILE_SNAPSHOTS) {
                    snapshots.removeLast();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<FileSnapshot> latest(int n) {
        lock.lock();
        try {
            if (snapshots.isEmpty()) return List.of();
            return List.copyOf(snapshots).subList(0, Math.min(n, snapshots.size()));
        } finally {
            lock.unlock();
        }
    }

    static String stripLinePrefixes(String content) {
        return LINE_PREFIX.matcher(content).replaceAll("");
    }

    private static String extractPath(Map<String, Object> parameters) {
        if (parameters == null) return null;
        Object path = parameters.get("path");
        return path != null ? path.toString() : null;
    }
}
