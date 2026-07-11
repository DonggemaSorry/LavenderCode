package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolMetadata {

    private static final Map<String, String> REGISTRY_TO_FRIENDLY = Map.of(
        "execute_command", "Bash",
        "read_file", "Read",
        "write_file", "Write",
        "edit_file", "Edit",
        "search_file", "Glob",
        "search_content", "Grep");

    private ToolMetadata() {}

    public static ToolCallContext from(ToolCall call, Path projectRoot) {
        String registryName = call.name();
        String friendlyName = REGISTRY_TO_FRIENDLY.getOrDefault(registryName, registryName);
        boolean parseFailed = call.parseError() != null || !REGISTRY_TO_FRIENDLY.containsKey(registryName);

        ToolCategory category = categoryFor(registryName);
        String matchKey = "";
        List<Path> sandboxPaths = new ArrayList<>();

        if (!parseFailed) {
            switch (registryName) {
                case "execute_command" -> matchKey = stringParam(call.parameters(), "command");
                case "read_file", "write_file", "edit_file" -> {
                    String pathStr = stringParam(call.parameters(), "path");
                    matchKey = toProjectRelativePath(pathStr, projectRoot);
                    sandboxPaths.add(resolvePath(pathStr, projectRoot));
                }
                case "search_file", "search_content" -> {
                    String dir = stringParamOrDefault(call.parameters(), "directory", ".");
                    matchKey = toProjectRelativePath(dir, projectRoot);
                    sandboxPaths.add(resolvePath(dir, projectRoot));
                }
                default -> parseFailed = true;
            }
        }

        return new ToolCallContext(
            call,
            registryName,
            friendlyName,
            category,
            matchKey,
            List.copyOf(sandboxPaths),
            projectRoot,
            parseFailed);
    }

    private static ToolCategory categoryFor(String registryName) {
        return switch (registryName) {
            case "read_file", "search_file", "search_content" -> ToolCategory.READ_ONLY;
            case "write_file", "edit_file" -> ToolCategory.FILE_WRITE;
            default -> ToolCategory.COMMAND;
        };
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringParamOrDefault(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private static Path resolvePath(String pathStr, Path projectRoot) {
        Path path = Paths.get(pathStr);
        if (path.isAbsolute()) {
            return path;
        }
        return projectRoot.resolve(path);
    }

    private static String toProjectRelativePath(String pathStr, Path projectRoot) {
        if (pathStr == null || pathStr.isBlank()) {
            return "";
        }
        if (".".equals(pathStr)) {
            return ".";
        }
        Path absolute = resolvePath(pathStr, projectRoot).toAbsolutePath().normalize();
        Path root = projectRoot.toAbsolutePath().normalize();
        if (absolute.startsWith(root)) {
            Path relative = root.relativize(absolute);
            if (relative.toString().isEmpty()) {
                return ".";
            }
            return relative.toString().replace('\\', '/');
        }
        return Paths.get(pathStr).normalize().toString().replace('\\', '/');
    }
}
