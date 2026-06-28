package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GlobToolTest {
    @TempDir
    Path dir;

    GlobTool tool = new GlobTool(200);

    @Test
    void matchesFilesByGlob() throws Exception {
        Files.writeString(dir.resolve("a.java"), "code");
        Files.writeString(dir.resolve("b.txt"), "text");
        Files.writeString(dir.resolve("c.java"), "code2");

        var r = tool.execute(Map.of("pattern", "*.java", "directory", dir.toString()));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("a.java").contains("c.java").doesNotContain("b.txt");
    }

    @Test
    void noMatches() throws Exception {
        var r = tool.execute(Map.of("pattern", "*.rs", "directory", dir.toString()));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).isBlank();
    }

    @Test
    void truncationEnabled() throws Exception {
        GlobTool limited = new GlobTool(2);
        for (int i = 1; i <= 5; i++) {
            Files.writeString(dir.resolve("file" + i + ".txt"), "content");
        }
        var r = limited.execute(Map.of("pattern", "*.txt", "directory", dir.toString()));
        assertThat(r.truncationInfo()).isNotNull();
        assertThat(r.truncationInfo().totalCount()).isEqualTo(5);
    }

    @Test
    void invalidPattern() {
        var r = tool.execute(Map.of("directory", dir.toString()));
        assertThat(r.errorCategory()).isEqualTo("INVALID_PARAMETER");
    }
}
