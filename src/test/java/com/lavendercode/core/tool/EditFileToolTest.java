package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class EditFileToolTest {
    @TempDir
    Path dir;

    EditFileTool tool = new EditFileTool();

    @Test
    void uniqueMatchReplaces() throws Exception {
        Path f = dir.resolve("test.txt");
        Files.writeString(f, "line1\nold_line\nline3");
        var r = tool.execute(Map.of("path", f.toString(), "old_string", "old_line", "new_string", "new_line"));
        assertThat(r.success()).isTrue();
        assertThat(r.summary()).contains("替换 1 处");
        assertThat(Files.readString(f)).isEqualTo("line1\nnew_line\nline3");
    }

    @Test
    void zeroMatchReturnsNoMatch() throws Exception {
        Path f = dir.resolve("nomatch.txt");
        Files.writeString(f, "some content");
        var r = tool.execute(Map.of("path", f.toString(), "old_string", "nonexistent", "new_string", "x"));
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("NO_MATCH");
    }

    @Test
    void multipleMatchesReturnsDetail() throws Exception {
        Path f = dir.resolve("multi.txt");
        Files.writeString(f, "dup\nmiddle\ndup\nmore\ndup");
        var r = tool.execute(Map.of("path", f.toString(), "old_string", "dup", "new_string", "x"));
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("MULTIPLE_MATCHES");
        assertThat(r.errorDetail()).contains("匹配到 3 处");
    }

    @Test
    void fileNotFound() {
        var r = tool.execute(Map.of("path", dir.resolve("nope.txt").toString(), "old_string", "x", "new_string", "y"));
        assertThat(r.errorCategory()).isEqualTo("FILE_NOT_FOUND");
    }
}
