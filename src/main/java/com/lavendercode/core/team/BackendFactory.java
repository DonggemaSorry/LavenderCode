package com.lavendercode.core.team;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class BackendFactory {
    private BackendFactory() {}

    public static BackendType detect() {
        String force = System.getenv("LAVENDERCODE_TEAM_BACKEND");
        if (force == null || force.isBlank()) {
            force = System.getProperty("LAVENDERCODE_TEAM_BACKEND");
        }
        if (force != null && "in-process".equalsIgnoreCase(force.trim())) {
            return BackendType.IN_PROCESS;
        }
        if (System.getenv("TMUX") != null) {
            return BackendType.TMUX;
        }
        if ("iTerm.app".equals(System.getenv("TERM_PROGRAM")) && onPath("it2")) {
            return BackendType.ITERM2;
        }
        if (onPath("tmux")) {
            return BackendType.TMUX;
        }
        return BackendType.IN_PROCESS;
    }

    /** 刀1：仅 IN_PROCESS 已实现。 */
    public static void requireImplemented(BackendType type) {
        if (type != BackendType.IN_PROCESS) {
            throw new BackendNotAvailableException(
                "执行后端 " + type.wireValue()
                    + " 尚未实现（刀2）。请设置环境变量或系统属性 LAVENDERCODE_TEAM_BACKEND=in-process");
        }
    }

    public static Backend create(BackendType type, com.lavendercode.core.task.TaskManager taskManager) {
        requireImplemented(type);
        return new InProcessBackend(taskManager);
    }

    static boolean onPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        String[] parts = path.split(path.contains(";") ? ";" : ":");
        String lower = exe.toLowerCase(Locale.ROOT);
        for (String dir : parts) {
            java.nio.file.Path p = java.nio.file.Path.of(dir);
            if (java.nio.file.Files.isExecutable(p.resolve(exe))
                || java.nio.file.Files.isExecutable(p.resolve(exe + ".exe"))) {
                return true;
            }
            // Windows where.exe / unix which (best-effort, may be slow — prefer Files check)
            if (java.nio.file.Files.exists(p.resolve(lower))
                || java.nio.file.Files.exists(p.resolve(lower + ".exe"))) {
                return true;
            }
        }
        return which(exe);
    }

    private static boolean which(String exe) {
        try {
            boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            ProcessBuilder pb = win
                ? new ProcessBuilder("where", exe)
                : new ProcessBuilder("which", exe);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    // drain
                }
            }
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
