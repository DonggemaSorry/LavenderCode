package com.lavendercode.core.worktree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

/** Best-effort post-create setup (F7–F10). Failures only warn. */
public final class WorktreeSetup {
    private static final List<String> LINK_DIRS = List.of("node_modules", ".venv", "vendor");

    private WorktreeSetup() {}

    public static void perform(Path repoRoot, Path wtPath, GitCli git) {
        copyLocalConfig(repoRoot, wtPath);
        configureHooks(repoRoot, wtPath, git);
        symlinkLargeDirs(repoRoot, wtPath);
        copyWorktreeInclude(repoRoot, wtPath, git);
    }

    static void copyLocalConfig(Path repoRoot, Path wtPath) {
        for (String name : List.of("config.yaml", "settings.local.yaml")) {
            Path src = repoRoot.resolve(".lavendercode").resolve(name);
            Path dst = wtPath.resolve(".lavendercode").resolve(name);
            try {
                if (Files.isRegularFile(src) && !Files.exists(dst)) {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst);
                }
            } catch (Exception e) {
                System.err.println("WARN: 复制配置失败 " + name + ": " + e.getMessage());
            }
        }
    }

    static void configureHooks(Path repoRoot, Path wtPath, GitCli git) {
        try {
            String hooksPath = null;
            try {
                hooksPath = git.run(repoRoot, List.of("config", "--get", "core.hooksPath"));
            } catch (GitCli.GitCliException ignored) {
                // unset
            }
            if (hooksPath == null || hooksPath.isBlank()) {
                Path husky = repoRoot.resolve(".husky");
                if (Files.isDirectory(husky)) {
                    hooksPath = husky.toAbsolutePath().toString();
                }
            }
            if (hooksPath != null && !hooksPath.isBlank()) {
                Path resolved = Path.of(hooksPath);
                if (!resolved.isAbsolute()) {
                    resolved = repoRoot.resolve(hooksPath).toAbsolutePath().normalize();
                }
                git.run(wtPath, List.of("config", "core.hooksPath", resolved.toString()));
            }
        } catch (Exception e) {
            System.err.println("WARN: 配置 hooksPath 失败: " + e.getMessage());
        }
    }

    static void symlinkLargeDirs(Path repoRoot, Path wtPath) {
        for (String name : LINK_DIRS) {
            Path src = repoRoot.resolve(name);
            Path dst = wtPath.resolve(name);
            try {
                if (Files.exists(src) && !Files.exists(dst)) {
                    Files.createSymbolicLink(dst, src.toAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println("WARN: 软链 " + name + " 失败: " + e.getMessage());
            }
        }
    }

    static void copyWorktreeInclude(Path repoRoot, Path wtPath, GitCli git) {
        Path include = repoRoot.resolve(".worktreeinclude");
        if (!Files.isRegularFile(include)) {
            return;
        }
        try {
            List<String> patterns = Files.readAllLines(include).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toList();
            if (patterns.isEmpty()) {
                return;
            }
            String listed;
            try {
                listed = git.run(repoRoot, List.of(
                    "ls-files", "--others", "--ignored", "--exclude-standard"));
            } catch (GitCli.GitCliException e) {
                System.err.println("WARN: ls-files ignored 失败: " + e.getMessage());
                return;
            }
            for (String line : listed.split("\n")) {
                String rel = line.strip();
                if (rel.isEmpty()) {
                    continue;
                }
                if (!matchesAny(rel, patterns)) {
                    continue;
                }
                Path src = repoRoot.resolve(rel);
                Path dst = wtPath.resolve(rel);
                try {
                    if (Files.isRegularFile(src) && !Files.exists(dst)) {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    System.err.println("WARN: 复制 include 文件失败 " + rel + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("WARN: 读取 .worktreeinclude 失败: " + e.getMessage());
        }
    }

    static boolean matchesAny(String path, List<String> patterns) {
        String name = Path.of(path).getFileName().toString();
        for (String p : patterns) {
            Pattern re = globToPattern(p);
            if (re.matcher(path).matches() || re.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.' -> sb.append("\\.");
                default -> {
                    if ("\\[]{}()+^$|".indexOf(c) >= 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }
}
