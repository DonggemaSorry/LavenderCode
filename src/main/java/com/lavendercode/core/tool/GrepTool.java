package com.lavendercode.core.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GrepTool implements Tool {
    private final int maxResults;

    public GrepTool(int maxResults) {
        this.maxResults = maxResults;
    }

    @Override
    public String name() {
        return "search_content";
    }

    @Override
    public String description() {
        return "Searches file contents for a pattern and returns matching lines with file paths.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "pattern", new ToolParameterSchema.PropertyDef("string", "搜索模式", null, null),
                "directory", new ToolParameterSchema.PropertyDef("string", "搜索根目录", null, null),
                "file_pattern", new ToolParameterSchema.PropertyDef("string", "限定文件 glob", null, null),
                "case_sensitive", new ToolParameterSchema.PropertyDef("boolean", "区分大小写", null, null)
            ), List.of("pattern"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pattern = (String) params.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "pattern 为空", "pattern is null or blank");
        }

        String dirStr = (String) params.get("directory");
        Path dir = dirStr != null ? Path.of(dirStr) : Path.of(System.getProperty("user.dir"));

        String filePattern = params.containsKey("file_pattern") ? (String) params.get("file_pattern") : "*";
        boolean caseSensitive = params.containsKey("case_sensitive") && Boolean.TRUE.equals(params.get("case_sensitive"));

        if (!Files.isDirectory(dir)) {
            return ToolResult.error("INVALID_PARAMETER", "目录不存在·" + dir, dir.toString());
        }

        String searchPattern = caseSensitive ? pattern : "(?i)" + pattern;

        try {
            PathMatcher fileMatcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
            List<String> results = new ArrayList<>();
            int totalFound = 0;

            try (Stream<Path> stream = Files.walk(dir)) {
                var it = stream.iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    if (!Files.isRegularFile(p)) continue;
                    if (p.getFileName() == null
                        || p.getFileName().toString().startsWith(".")) continue;

                    Path relative = dir.relativize(p);
                    if (!fileMatcher.matches(p.getFileName()) && !fileMatcher.matches(relative)) continue;

                    // Skip likely binary files
                    if (isLikelyBinary(p)) continue;

                    try {
                        List<String> lines = Files.readAllLines(p);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            boolean match;
                            if (caseSensitive) {
                                match = line.contains(pattern);
                            } else {
                                match = line.toLowerCase().contains(pattern.toLowerCase());
                            }
                            if (match) {
                                totalFound++;
                                if (results.size() < maxResults) {
                                    results.add(relative + ":" + (i + 1) + ": " + line);
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // Skip unreadable files
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            for (String r : results) {
                sb.append(r).append("\n");
            }
            String content = sb.toString();
            String summary = results.size() + " 处匹配";

            if (totalFound > maxResults) {
                return ToolResult.success(summary, content,
                    new TruncationInfo(totalFound, results.size(), 0, maxResults));
            }
            return ToolResult.success(summary, content);
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "搜索失败", e.getMessage());
        }
    }

    private boolean isLikelyBinary(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[512];
            int n = in.read(buf);
            for (int i = 0; i < n; i++) {
                if (buf[i] == 0) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
