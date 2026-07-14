package com.lavendercode.core.skill;

import com.lavendercode.core.tool.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InstallSkillTool implements Tool {

    private static final int MAX_FILE_SIZE = 1 * 1024 * 1024;       // 1 MiB
    private static final int MAX_TOTAL_SIZE = 8 * 1024 * 1024;      // 8 MiB
    private static final int MAX_FILE_COUNT = 64;
    private static final int MAX_DEPTH = 4;

    public enum SourceType { GITHUB_TREE, RAW_FILE }

    public record RemoteSource(SourceType type, String owner, String repo,
                                String branch, String path, String dirName) {}

    public record RemoteFile(String path, byte[] content, int depth) {}

    private final SkillCatalog catalog;
    private final Path skillsDir;
    private final Runnable postInstallHook;

    public InstallSkillTool(SkillCatalog catalog, Path skillsDir, Runnable postInstallHook) {
        this.catalog = catalog;
        this.skillsDir = skillsDir;
        this.postInstallHook = postInstallHook;
    }

    @Override
    public String name() { return "install_skill"; }

    @Override
    public String description() {
        return "从 GitHub URL 安装技能到本地 .lavendercode/skills/ 目录";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            java.util.Map.of(
                "url", new ToolParameterSchema.PropertyDef(
                    "string", "GitHub 仓库 URL（支持 tree 和 raw 格式）", null, null)),
            List.of("url"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String url = (String) params.get("url");
        if (url == null || url.isBlank()) {
            return ToolResult.error("VALIDATION", "URL 为空", "请提供 GitHub URL");
        }
        RemoteSource source = parseUrl(url);
        if (source == null) {
            return ToolResult.error("UNSUPPORTED_URL", "不支持的 URL 格式",
                "支持的格式: github.com/<owner>/<repo>/tree/<branch>/<path> 或 "
                + "raw.githubusercontent.com/<owner>/<repo>/<branch>/<file>");
        }
        try {
            List<RemoteFile> files = fetchTree(source);
            validateLimits(files);
            Path tempDir = stageToTemp(files);
            ensureSkillMd(tempDir);
            Path target = skillsDir.resolve(source.dirName());
            Files.createDirectories(skillsDir);
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            Files.move(tempDir, target, StandardCopyOption.ATOMIC_MOVE);
            catalog.reload();
            catalog.loadCatalog(skillsDir.getParent().getParent());
            if (postInstallHook != null) postInstallHook.run();
            return ToolResult.success("安装成功",
                "已安装技能到 " + target);
        } catch (Exception e) {
            return ToolResult.error("INSTALL_FAILED", "安装失败", e.getMessage());
        }
    }

    static RemoteSource parseUrl(String url) {
        if (url == null) return null;
        // github.com/<owner>/<repo>/tree/<branch>/<path>
        if (url.contains("github.com")) {
            String trimmed = url.replace("https://", "").replace("http://", "");
            String[] parts = trimmed.split("/", 6);
            // parts: [0]github.com [1]owner [2]repo [3]tree [4]branch [5]path
            if (parts.length >= 6 && parts[3].equals("tree")) {
                String owner = parts[1];
                String repo = parts[2];
                String branch = parts[4];
                String path = parts[5];
                String dirName = path.contains("/")
                    ? path.substring(path.lastIndexOf('/') + 1) : path;
                return new RemoteSource(SourceType.GITHUB_TREE, owner, repo, branch, path, dirName);
            }
        }
        // raw.githubusercontent.com/<owner>/<repo>/<branch>/<file>
        if (url.contains("raw.githubusercontent.com")) {
            String trimmed = url.replace("https://", "").replace("http://", "");
            String[] parts = trimmed.split("/", 5);
            // parts: [0]raw.githubusercontent.com [1]owner [2]repo [3]branch [4]path
            if (parts.length >= 5) {
                String owner = parts[1];
                String repo = parts[2];
                String branch = parts[3];
                String path = parts[4];
                String dirName = path.contains("/")
                    ? path.substring(0, path.indexOf('/')) : path.replace(".md", "");
                return new RemoteSource(SourceType.RAW_FILE, owner, repo, branch, path, dirName);
            }
        }
        return null;
    }

    List<RemoteFile> fetchTree(RemoteSource source) throws IOException {
        // TODO: 使用 GitHub Contents API 拉取文件
        // 此处为骨架实现，实际需要 HTTP 请求
        throw new UnsupportedOperationException("fetchTree 尚未实现，需要 HTTP 客户端");
    }

    static void validateLimits(List<RemoteFile> files) {
        if (files.size() > MAX_FILE_COUNT) {
            throw new IllegalStateException(
                "文件数超过上限: " + files.size() + " > " + MAX_FILE_COUNT);
        }
        long total = 0;
        for (RemoteFile f : files) {
            if (f.content().length > MAX_FILE_SIZE) {
                throw new IllegalStateException(
                    "单文件超过上限: " + f.path() + " (" + f.content().length + " bytes)");
            }
            total += f.content().length;
            int depth = countSlashes(f.path());
            if (depth > MAX_DEPTH) {
                throw new IllegalStateException(
                    "目录深度超过上限: " + f.path() + " (" + depth + " > " + MAX_DEPTH + ")");
            }
        }
        if (total >= MAX_TOTAL_SIZE) {
            throw new IllegalStateException(
                "总大小超过上限: " + total + " >= " + MAX_TOTAL_SIZE);
        }
    }

    static void ensureSkillMd(Path dir) {
        if (!Files.exists(dir.resolve("SKILL.md"))) {
            throw new IllegalStateException("安装目录缺少 SKILL.md");
        }
    }

    private Path stageToTemp(List<RemoteFile> files) throws IOException {
        Path temp = Files.createTempDirectory("skill-install-");
        for (RemoteFile f : files) {
            Path target = temp.resolve(f.path());
            Files.createDirectories(target.getParent());
            Files.write(target, f.content());
        }
        return temp;
    }

    private static int countSlashes(String path) {
        int count = 0;
        for (char c : path.toCharArray()) {
            if (c == '/') count++;
        }
        return count;
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try { deleteRecursive(p); } catch (IOException ignored) {}
                });
            }
        }
        Files.deleteIfExists(path);
    }
}
