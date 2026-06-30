package com.lavendercode.core.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class WriteFileTool implements Tool {
    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Writes content to a file, creating it and parent directories if needed. Overwrites existing files.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "path", new ToolParameterSchema.PropertyDef("string", "文件路径（相对路径将基于工作目录解析）", null, null),
                "content", new ToolParameterSchema.PropertyDef("string", "要写入的完整内容", null, null)
            ), List.of("path", "content"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String pathStr = (String) params.get("path");
        String content = (String) params.get("content");
        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "路径无效", "path is null or blank");
        }
        Path path = Path.of(pathStr);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(pathStr).normalize();
        }
        if (content == null) {
            return ToolResult.error("INVALID_PARAMETER", "内容为空", "content is null");
        }

        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content);
            String fileName = path.getFileName() != null ? path.getFileName().toString() : pathStr;
            long byteCount = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return ToolResult.success("写入 " + byteCount + " 字节·" + fileName, content);
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "写入失败", e.getMessage());
        }
    }
}
