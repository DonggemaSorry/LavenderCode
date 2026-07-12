package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewBuilderTest {
    @Test
    void includesFourRequiredElements() {
        String content = "line1\nline2\n";
        String preview = PreviewBuilder.build(content, 100, Path.of("/tmp/tool-results/id1"));
        assertThat(preview).contains("Original size: 100 bytes");
        assertThat(preview).contains("Preview:");
        assertThat(preview).contains("Full content saved to:");
        assertThat(preview).contains("read_file");
    }

    @Test
    void headRespectsLineAndByteLimits() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append("line").append(i).append("\n");
        String content = sb.toString();
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        String preview = PreviewBuilder.build(content, bytes, Path.of("out"));
        String headSection = preview.substring(preview.indexOf("Preview:"));
        assertThat(headSection.getBytes(StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(ContextConstants.PREVIEW_MAX_BYTES + 512);
    }
}
