package com.lavendercode.core.context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class PreviewBuilder {
    private PreviewBuilder() {}

    public static String build(String content, int originalBytes, Path absolutePath) {
        String head = truncateHead(content);
        return """
            [Tool result offloaded]
            Original size: %d bytes
            Preview:
            %s
            ...
            Full content saved to: %s
            To read the complete output, use the read_file tool on the path above.
            """.formatted(originalBytes, head, absolutePath.toAbsolutePath().normalize());
    }

    static String truncateHead(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder byLines = new StringBuilder();
        int lineLimit = Math.min(ContextConstants.PREVIEW_MAX_LINES, lines.length);
        for (int i = 0; i < lineLimit; i++) {
            if (i > 0) byLines.append('\n');
            byLines.append(lines[i]);
        }
        String lineTruncated = byLines.toString();
        byte[] lineBytes = lineTruncated.getBytes(StandardCharsets.UTF_8);
        if (lineBytes.length <= ContextConstants.PREVIEW_MAX_BYTES) {
            return lineTruncated;
        }
        int end = ContextConstants.PREVIEW_MAX_BYTES;
        while (end > 0 && (lineBytes[end - 1] & 0xC0) == 0x80) end--;
        return new String(lineBytes, 0, end, StandardCharsets.UTF_8);
    }
}
