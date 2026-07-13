package com.lavendercode.core.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryServiceTest {

    @TempDir
    Path projectRoot;

    @TempDir
    Path userHome;

    @Test
    void currentIndexIsEmptyBeforeLoad() {
        MemoryService service = new MemoryService(projectRoot, userHome);

        assertThat(service.currentIndex()).isEmpty();
    }

    @Test
    void loadIndexReadsProjectThenUserMemoryFilesAndCachesResult() throws Exception {
        Path projectMemory = projectRoot.resolve(".lavendercode/memory");
        Path userMemory = userHome.resolve(".lavendercode/memory");
        Files.createDirectories(projectMemory);
        Files.createDirectories(userMemory);
        Files.writeString(projectMemory.resolve("MEMORY.md"), "project memory");
        Files.writeString(userMemory.resolve("MEMORY.md"), "user memory");

        MemoryService service = new MemoryService(projectRoot, userHome);

        assertThat(service.loadIndex()).isEqualTo("project memory\nuser memory");
        Files.writeString(projectMemory.resolve("MEMORY.md"), "changed");
        assertThat(service.currentIndex()).isEqualTo("project memory\nuser memory");
    }

    @Test
    void loadIndexSkipsMissingFiles() throws Exception {
        Path userMemory = userHome.resolve(".lavendercode/memory");
        Files.createDirectories(userMemory);
        Files.writeString(userMemory.resolve("MEMORY.md"), "user only");

        MemoryService service = new MemoryService(projectRoot, userHome);

        assertThat(service.loadIndex()).isEqualTo("user only");
    }

    @Test
    void loadIndexTruncatesCombinedIndexToTwentyFiveKilobytes() throws Exception {
        Path projectMemory = projectRoot.resolve(".lavendercode/memory");
        Files.createDirectories(projectMemory);
        Files.writeString(projectMemory.resolve("MEMORY.md"), "x".repeat(26 * 1024));

        MemoryService service = new MemoryService(projectRoot, userHome);

        String index = service.loadIndex();
        assertThat(index).hasSize(25 * 1024 + "\n(index truncated)".length());
        assertThat(index).endsWith("\n(index truncated)");
    }

    @Test
    void noteTypesMatchPersistedNames() {
        assertThat(MemoryNoteType.user_preference.name()).isEqualTo("user_preference");
        assertThat(MemoryNoteType.correction_feedback.name()).isEqualTo("correction_feedback");
        assertThat(MemoryNoteType.project_knowledge.name()).isEqualTo("project_knowledge");
        assertThat(MemoryNoteType.reference_material.name()).isEqualTo("reference_material");
    }
}
