package com.lavendercode.core.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InstructionLoader {
    private static final String INSTRUCTION_FILE = "LAVENDERCODE.md";
    private static final int MAX_DEPTH = 5;
    private static final int BINARY_PROBE_BYTES = 512;
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s+(\\S+)\\s*$");

    private InstructionLoader() {
    }

    public static String load(Path projectRoot) {
        return load(projectRoot, Path.of(System.getProperty("user.home")));
    }

    public static String load(Path projectRoot, Path userHome) {
        List<String> layers = new ArrayList<>();
        addLayer(layers, projectRoot.resolve(INSTRUCTION_FILE), projectRoot);
        addLayer(layers, projectRoot.resolve(".lavendercode").resolve(INSTRUCTION_FILE), projectRoot);

        Path userBoundary = userHome.resolve(".lavendercode");
        addLayer(layers, userBoundary.resolve(INSTRUCTION_FILE), userBoundary);
        return String.join("\n\n", layers);
    }

    private static void addLayer(List<String> layers, Path file, Path boundary) {
        loadLayer(file, boundary).ifPresent(layers::add);
    }

    private static Optional<String> loadLayer(Path file, Path boundary) {
        try {
            String content = expandFile(normalize(file), normalize(boundary), new HashSet<>(), 1);
            if (content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String expandFile(Path file, Path boundary, Set<Path> visited, int depth) throws IOException {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        if (isBinary(file)) {
            return binaryWarning(file);
        }
        if (!visited.add(file)) {
            return cycleWarning(file);
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return "";
            }
            return expandContent(content, file.getParent(), boundary, visited, depth);
        } finally {
            visited.remove(file);
        }
    }

    private static String expandContent(
        String content,
        Path currentDirectory,
        Path boundary,
        Set<Path> visited,
        int depth
    ) throws IOException {
        List<String> expanded = new ArrayList<>();
        for (String line : content.split("\\R", -1)) {
            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                expanded.add(line);
                continue;
            }

            String includePath = matcher.group(1);
            Path included = normalize(currentDirectory.resolve(includePath));
            String replacement = expandInclude(line, included, boundary, visited, depth + 1);
            if (!replacement.isEmpty()) {
                expanded.add(replacement);
            }
        }
        return String.join("\n", expanded);
    }

    private static String expandInclude(
        String originalLine,
        Path included,
        Path boundary,
        Set<Path> visited,
        int nextDepth
    ) throws IOException {
        if (!included.startsWith(boundary)) {
            return escapeWarning(included);
        }
        if (visited.contains(included)) {
            return cycleWarning(included);
        }
        if (nextDepth > MAX_DEPTH) {
            return originalLine + "\n" + depthWarning(included);
        }
        return expandFile(included, boundary, visited, nextDepth);
    }

    private static boolean isBinary(Path file) throws IOException {
        byte[] buffer = new byte[BINARY_PROBE_BYTES];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(buffer);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String depthWarning(Path path) {
        return "<!-- @include 超过最大嵌套深度，已跳过: " + path + " -->";
    }

    private static String cycleWarning(Path path) {
        return "<!-- @include 检测到环路，已跳过: " + path + " -->";
    }

    private static String escapeWarning(Path path) {
        return "<!-- @include 路径超出允许范围，已跳过: " + path + " -->";
    }

    private static String binaryWarning(Path path) {
        return "<!-- @include 检测到二进制文件，已跳过: " + path + " -->";
    }
}
