package com.lavendercode.core.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryServiceFileListTest {

    @TempDir
    Path tempDir;

    @Test
    void listProjectMemoryFileNames() throws Exception {
        var projectRoot = tempDir.resolve("project");
        var userHome = tempDir.resolve("home");
        Files.createDirectories(projectRoot.resolve(".lavendercode/memory"));
        Files.writeString(projectRoot.resolve(".lavendercode/memory/CODING_STANDARD_rules.md"), "# Rules");
        Files.writeString(projectRoot.resolve(".lavendercode/memory/MEMORY.md"), "# Index");

        var service = new MemoryService(projectRoot, userHome);
        var names = service.projectMemoryFileNames();
        assertThat(names).containsExactly("CODING_STANDARD_rules.md");
        assertThat(names).doesNotContain("MEMORY.md");
    }

    @Test
    void listUserMemoryFileNames() throws Exception {
        var projectRoot = tempDir.resolve("project");
        var userHome = tempDir.resolve("home");
        Files.createDirectories(userHome.resolve(".lavendercode/memory"));
        Files.writeString(userHome.resolve(".lavendercode/memory/user-prefs.md"), "# Prefs");

        var service = new MemoryService(projectRoot, userHome);
        assertThat(service.userMemoryFileNames()).containsExactly("user-prefs.md");
    }

    @Test
    void fileNamesCombinesBothLayers() throws Exception {
        var projectRoot = tempDir.resolve("project");
        var userHome = tempDir.resolve("home");
        Files.createDirectories(projectRoot.resolve(".lavendercode/memory"));
        Files.createDirectories(userHome.resolve(".lavendercode/memory"));
        Files.writeString(projectRoot.resolve(".lavendercode/memory/p1.md"), "x");
        Files.writeString(userHome.resolve(".lavendercode/memory/u1.md"), "y");

        var service = new MemoryService(projectRoot, userHome);
        assertThat(service.fileNames()).containsExactlyInAnyOrder("p1.md", "u1.md");
    }

    @Test
    void emptyDirReturnsEmptyList() {
        var service = new MemoryService(tempDir.resolve("p"), tempDir.resolve("u"));
        assertThat(service.projectMemoryFileNames()).isEmpty();
        assertThat(service.userMemoryFileNames()).isEmpty();
        assertThat(service.fileNames()).isEmpty();
    }
}
