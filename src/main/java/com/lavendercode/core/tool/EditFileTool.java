package com.lavendercode.core.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditFileTool implements Tool {
    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Replaces a uniquely matched text fragment in a file. old_string must match exactly once.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "path", new ToolParameterSchema.PropertyDef("string", "文件路径（相对路径将基于工作目录解析）", null, null),
                "old_string", new ToolParameterSchema.PropertyDef("string", "要替换的原文片段（必须唯一匹配）", null, null),
                "new_string", new ToolParameterSchema.PropertyDef("string", "替换后的新文片段", null, null)
            ), List.of("path", "old_string", "new_string"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pathStr = (String) params.get("path");
        String oldStr = (String) params.get("old_string");
        String newStr = (String) params.get("new_string");

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "路径无效", "path is null or blank");
        }
        Path path = Path.of(pathStr);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(pathStr).normalize();
        }
        if (oldStr == null || oldStr.isEmpty()) {
            return ToolResult.error("INVALID_PARAMETER", "old_string 为空", "old_string is null or empty");
        }
        if (!Files.exists(path)) {
            return ToolResult.error("FILE_NOT_FOUND", "文件不存在·" + pathStr, pathStr);
        }

        try {
            String content = Files.readString(path);
            List<Integer> positions = new ArrayList<>();
            int idx = 0;
            while ((idx = content.indexOf(oldStr, idx)) != -1) {
                positions.add(idx);
                idx += oldStr.length();
            }

            if (positions.isEmpty()) {
                String excerpt = oldStr.length() > 80 ? oldStr.substring(0, 80) + "..." : oldStr;
                return ToolResult.error("NO_MATCH",
                    "未找到匹配内容",
                    "在 " + pathStr + " 中未找到指定的原文片段: " + excerpt);
            }

            if (positions.size() == 1) {
                String result = content.substring(0, positions.get(0))
                    + newStr
                    + content.substring(positions.get(0) + oldStr.length());
                Files.writeString(path, result);
                String fileName = path.getFileName() != null ? path.getFileName().toString() : pathStr;
                return ToolResult.success("替换 1 处·" + fileName, "替换成功");
            }

            // Multiple matches - build error detail with positions
            String[] lines = content.split("\n", -1);
            StringBuilder detail = new StringBuilder();
            detail.append("匹配到 ").append(positions.size())
                .append(" 处内容，请提供更多上下文使匹配唯一：\n");
            int maxShow = Math.min(positions.size(), 5);
            for (int i = 0; i < maxShow; i++) {
                int pos = positions.get(i);
                int lineNum = findLineNumber(content, pos);
                int zeroBasedLine = lineNum - 1;
                detail.append("  第").append(lineNum).append("行: ");
                if (zeroBasedLine < lines.length) {
                    detail.append(lines[zeroBasedLine]).append("\n");
                }
                if (zeroBasedLine - 1 >= 0) {
                    detail.append("  - ").append(lines[zeroBasedLine - 1]).append("\n");
                }
                if (zeroBasedLine < lines.length) {
                    detail.append("  + ").append(lines[zeroBasedLine]).append("\n");
                }
                if (zeroBasedLine + 1 < lines.length) {
                    detail.append("  - ").append(lines[zeroBasedLine + 1]).append("\n");
                }
            }
            return ToolResult.error("MULTIPLE_MATCHES", "匹配到 " + positions.size() + " 处", detail.toString());
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "操作失败", e.getMessage());
        }
    }

    private int findLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }
}
