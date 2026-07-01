package com.lavendercode.core.tool;

public record ToolResult(boolean success, String summary, String content, String errorCategory, String errorDetail, TruncationInfo truncationInfo) {
    public static ToolResult success(String summary, String content) {
        return new ToolResult(true, summary, content, null, null, null);
    }

    public static ToolResult success(String summary, String content, TruncationInfo t) {
        return new ToolResult(true, summary, content, null, null, t);
    }

    public static ToolResult error(String category, String summary, String detail) {
        return new ToolResult(false, summary, null, category, detail, null);
    }

    public static ToolResult cancelled(String toolName) {
        return new ToolResult(false, "已取消·" + toolName, null, "CANCELLED", "用户中断", null);
    }
}
