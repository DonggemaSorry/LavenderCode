package com.lavendercode.core.tool;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GlobTool implements Tool {
    private final int maxResults;

    public GlobTool(int maxResults) {
        this.maxResults = maxResults;
    }

    @Override
    public String name() {
        return "search_file";
    }

    @Override
    public String description() {
        return "Finds files matching a glob pattern in a directory.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "pattern", new ToolParameterSchema.PropertyDef("string", "Glob 模式", null, null),
                "directory", new ToolParameterSchema.PropertyDef("string", "搜索根目录", null, null)
            ), List.of("pattern"));
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        return execute(ToolContext.empty(), params);
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> params) {
        String pattern = (String) params.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "pattern 为空", "pattern is null or blank");
        }

        String dirStr = (String) params.get("directory");
        Path dir = dirStr != null ? ctx.resolvePath(dirStr) : ctx.resolvePath("");

        if (!Files.isDirectory(dir)) {
            return ToolResult.error("INVALID_PARAMETER", "目录不存在·" + dir, dir.toString());
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> matches = new ArrayList<>();
            int totalFound = 0;

            try (Stream<Path> stream = Files.walk(dir)) {
                var it = stream.iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    // Skip hidden directories
                    if (Files.isDirectory(p) && p.getFileName() != null
                        && p.getFileName().toString().startsWith(".")) {
                        continue;
                    }
                    Path relative = dir.relativize(p);
                    if (matcher.matches(p.getFileName()) || matcher.matches(relative)) {
                        totalFound++;
                        if (matches.size() < maxResults) {
                            matches.add(relative.toString());
                        }
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            for (String m : matches) {
                sb.append(m).append("\n");
            }
            String content = sb.toString();
            String summary = matches.size() + " 个文件";

            if (totalFound > maxResults) {
                return ToolResult.success(summary, content,
                    new TruncationInfo(totalFound, matches.size(), 0, maxResults));
            }
            return ToolResult.success(summary, content);
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "搜索失败", e.getMessage());
        }
    }
}
