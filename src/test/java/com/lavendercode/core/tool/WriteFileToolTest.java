package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class WriteFileToolTest {
    @TempDir
    Path dir;

    WriteFileTool tool = new WriteFileTool();

    @Test
    void createsNewFile() throws Exception {
        Path f = dir.resolve("new.txt");
        var r = tool.execute(Map.of("path", f.toString(), "content", "hello"));
        assertThat(r.success()).isTrue();
        assertThat(Files.readString(f)).isEqualTo("hello");
        assertThat(r.summary()).contains("写入").contains("new.txt");
    }

    @Test
    void overwritesExisting() throws Exception {
        Path f = dir.resolve("exist.txt");
        Files.writeString(f, "old");
        var r = tool.execute(Map.of("path", f.toString(), "content", "new"));
        assertThat(r.success()).isTrue();
        assertThat(Files.readString(f)).isEqualTo("new");
    }

    @Test
    void autoCreatesParentDirs() throws Exception {
        Path f = dir.resolve("sub").resolve("deep").resolve("file.txt");
        var r = tool.execute(Map.of("path", f.toString(), "content", "deep content"));
        assertThat(r.success()).isTrue();
        assertThat(Files.readString(f)).isEqualTo("deep content");
    }

    @Test
    void relativePathIsResolved() {
        // Relative paths are now resolved against the working directory
        var r = tool.execute(Map.of("path", "relative.txt", "content", "x"));
        assertThat(r.success()).isTrue();
    }
}
