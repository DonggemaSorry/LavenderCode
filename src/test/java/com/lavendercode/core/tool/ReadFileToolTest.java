package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {
    @TempDir
    Path dir;

    ReadFileTool tool = new ReadFileTool(2000);

    @Test
    void readsWithLineNumbers() throws Exception {
        Files.writeString(dir.resolve("f.txt"), "a\nb\nc");
        var r = tool.execute(Map.of("path", dir.resolve("f.txt").toString()));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("1: a").contains("3: c");
    }

    @Test
    void fileNotFound() {
        var r = tool.execute(Map.of("path", dir.resolve("nope.txt").toString()));
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    void offsetAndLimit() throws Exception {
        Path f = dir.resolve("l.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) sb.append("L").append(i).append("\n");
        Files.writeString(f, sb.toString());
        var r = tool.execute(Map.of("path", f.toString(), "offset", 10, "limit", 5));
        assertThat(r.content()).contains("10: L10").doesNotContain("15: L15");
    }

    @Test
    void truncation() throws Exception {
        Path f = dir.resolve("b.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) sb.append("line\n");
        Files.writeString(f, sb.toString());
        ReadFileTool rt = new ReadFileTool(10);
        var r = rt.execute(Map.of("path", f.toString()));
        assertThat(r.truncationInfo()).isNotNull();
        assertThat(r.truncationInfo().totalCount()).isEqualTo(100);
    }

    @Test
    void relativePathIsResolved() {
        // Relative paths are now resolved against the working directory instead of being rejected
        var r = tool.execute(Map.of("path", "relative.txt"));
        assertThat(r.success()).isTrue();
    }
}
