package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class SessionPathsTest {
    @TempDir Path projectRoot;

    @Test
    void toolResultPathUnderLavendercodeSessions() {
        SessionPaths paths = new SessionPaths(projectRoot, "1700000000-abc123");
        Path p = paths.toolResultPath("toolu_01");
        assertThat(p.toString().replace('\\', '/'))
            .endsWith(".lavendercode/sessions/1700000000-abc123/tool-results/toolu_01");
    }

    @Test
    void ensureDirectoriesCreatesToolResultsDir() throws Exception {
        SessionPaths paths = new SessionPaths(projectRoot, "sess-1");
        paths.ensureDirectories();
        assertThat(Files.isDirectory(paths.toolResultsDir())).isTrue();
    }

    @Test
    void writeToolResultIsIdempotent() throws Exception {
        SessionPaths paths = new SessionPaths(projectRoot, "sess-1");
        paths.ensureDirectories();
        paths.writeToolResult("id1", "hello");
        paths.writeToolResult("id1", "ignored");
        assertThat(Files.readString(paths.toolResultPath("id1"))).isEqualTo("hello");
    }
}
