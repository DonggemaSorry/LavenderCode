package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GrepToolTest {
    @TempDir
    Path dir;

    GrepTool tool = new GrepTool(200);

    @Test
    void searchContentMatches() throws Exception {
        Files.writeString(dir.resolve("a.java"), "public class Test {\n  private String name;\n}");
        Files.writeString(dir.resolve("b.txt"), "public interface Handler {\n  void handle();\n}");

        var r = tool.execute(Map.of("pattern", "public", "directory", dir.toString()));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("a.java").contains("b.txt");
    }

    @Test
    void caseInsensitiveByDefault() throws Exception {
        Files.writeString(dir.resolve("f.txt"), "HELLO\nworld");
        var r = tool.execute(Map.of("pattern", "hello", "directory", dir.toString()));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("HELLO");
    }

    @Test
    void filePatternFilter() throws Exception {
        Files.writeString(dir.resolve("a.java"), "public class A {}");
        Files.writeString(dir.resolve("b.txt"), "public content");
        var r = tool.execute(Map.of("pattern", "public", "directory", dir.toString(), "file_pattern", "*.txt"));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("b.txt").doesNotContain("a.java");
    }

    @Test
    void truncation() throws Exception {
        GrepTool limited = new GrepTool(2);
        for (int i = 1; i <= 5; i++) {
            Files.writeString(dir.resolve("f" + i + ".txt"), "match here");
        }
        var r = limited.execute(Map.of("pattern", "match", "directory", dir.toString()));
        assertThat(r.truncationInfo()).isNotNull();
        assertThat(r.truncationInfo().totalCount()).isEqualTo(5);
    }
}
