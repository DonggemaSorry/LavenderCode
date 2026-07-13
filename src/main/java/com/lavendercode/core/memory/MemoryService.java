package com.lavendercode.core.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MemoryService {
    private static final int MAX_INDEX_CHARS = 25 * 1024;
    private static final String TRUNCATED_MARKER = "\n(index truncated)";

    private final Path projectMemoryDir;
    private final Path userMemoryDir;
    private volatile String indexCache = "";

    public MemoryService(Path projectRoot, Path userHome) {
        this.projectMemoryDir = projectRoot.resolve(".lavendercode/memory");
        this.userMemoryDir = userHome.resolve(".lavendercode/memory");
    }

    public String loadIndex() throws IOException {
        List<String> indexes = new ArrayList<>();
        readIndex(projectMemoryDir).ifPresent(indexes::add);
        readIndex(userMemoryDir).ifPresent(indexes::add);

        String index = truncate(String.join("\n", indexes));
        indexCache = index;
        return index;
    }

    public String currentIndex() {
        return indexCache;
    }

    private static java.util.Optional<String> readIndex(Path memoryDir) throws IOException {
        Path index = memoryDir.resolve("MEMORY.md");
        if (!Files.exists(index)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Files.readString(index));
    }

    private static String truncate(String index) {
        if (index.length() <= MAX_INDEX_CHARS) {
            return index;
        }
        return index.substring(0, MAX_INDEX_CHARS) + TRUNCATED_MARKER;
    }
}
