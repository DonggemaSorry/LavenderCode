package com.lavendercode.core.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements Tool {
    private final int maxLines;

    public ReadFileTool(int maxLines) {
        this.maxLines = maxLines;
    }

    public ReadFileTool() {
        this(2000);
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Reads a file from the local filesystem and returns its content with line numbers.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "path", new ToolParameterSchema.PropertyDef("string", "文件路径（相对路径将基于工作目录解析）", null, null),
                "offset", new ToolParameterSchema.PropertyDef("integer", "起始行号(1-based)", null, null),
                "limit", new ToolParameterSchema.PropertyDef("integer", "最大读取行数", null, null)
            ), List.of("path"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pathStr = (String) params.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "路径无效", "path is null or blank");
        }
        Path path = Path.of(pathStr);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(pathStr).normalize();
        }
        if (!Files.exists(path)) {
            return ToolResult.error("FILE_NOT_FOUND", "文件不存在·" + pathStr, pathStr);
        }
        if (!Files.isReadable(path)) {
            return ToolResult.error("FILE_NOT_READABLE", "文件不可读·" + pathStr, pathStr);
        }

        int offset = params.containsKey("offset") ? ((Number) params.get("offset")).intValue() : 1;
        int limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : maxLines;
        if (offset < 1) offset = 1;
        if (limit < 1) limit = maxLines;

        try {
            List<String> allLines = Files.readAllLines(path);
            int total = allLines.size();
            int startIdx = Math.max(0, offset - 1);
            int endIdx = Math.min(allLines.size(), startIdx + limit);
            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                int lineNum = i + 1;
                if (lineNum < 10) sb.append("   ");
                else if (lineNum < 100) sb.append("  ");
                else if (lineNum < 1000) sb.append(" ");
                sb.append(lineNum).append(": ").append(allLines.get(i)).append("\n");
            }
            String content = sb.toString();
            boolean truncated = endIdx < total;
            String fileName = path.getFileName() != null ? path.getFileName().toString() : pathStr;
            int displayed = endIdx - startIdx;
            String summary = displayed + " 行·" + fileName;
            if (truncated) {
                return ToolResult.success(summary, content,
                    new TruncationInfo(total, displayed, offset - 1, limit));
            }
            return ToolResult.success(summary, content);
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "读取失败", e.getMessage());
        }
    }
}
