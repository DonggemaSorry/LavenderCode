package com.lavendercode.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private McpConfigLoader() {}

    public static List<McpServerConfig> load(Path projectRoot) {
        Path userHome = Path.of(System.getProperty("user.home")).resolve(".LavenderCode");
        return load(projectRoot, userHome);
    }

    public static List<McpServerConfig> load(Path projectRoot, Path userConfigDir) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        mergeTier(merged, userConfigDir.resolve("config.yaml"));
        mergeTier(merged, projectRoot.resolve(".LavenderCode.yaml"));

        List<McpServerConfig> out = new ArrayList<>();
        for (var entry : merged.entrySet()) {
            parseServer(entry.getKey(), entry.getValue()).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static void mergeTier(Map<String, JsonNode> merged, Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            JsonNode servers = root.get("mcp_servers");
            if (servers == null || !servers.isObject()) {
                return;
            }
            servers.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        } catch (IOException ex) {
            System.err.println("WARN: failed to parse MCP config " + path + ": " + ex.getMessage());
        }
    }

    private static Optional<McpServerConfig> parseServer(String name, JsonNode node) {
        McpServerType type = McpServerType.fromYaml(text(node, "type"));
        if (type == null) {
            System.err.println("WARN: MCP server '" + name + "' skipped: invalid or missing type");
            return Optional.empty();
        }
        return switch (type) {
            case STDIO -> parseStdio(name, node);
            case HTTP -> parseHttp(name, node);
        };
    }

    private static Optional<McpServerConfig> parseStdio(String name, JsonNode node) {
        String command = text(node, "command");
        if (command == null || command.isBlank()) {
            System.err.println("WARN: MCP server '" + name + "' skipped: stdio requires command");
            return Optional.empty();
        }
        List<String> args = readStringList(node.get("args"));
        Map<String, String> env = EnvExpander.expandValues(readStringMap(node.get("env")), name);
        return Optional.of(McpServerConfig.stdio(name, command, args, env));
    }

    private static Optional<McpServerConfig> parseHttp(String name, JsonNode node) {
        String url = text(node, "url");
        if (url == null || url.isBlank()) {
            System.err.println("WARN: MCP server '" + name + "' skipped: http requires url");
            return Optional.empty();
        }
        Map<String, String> headers = EnvExpander.expandValues(readStringMap(node.get("headers")), name);
        return Optional.of(McpServerConfig.http(name, url, headers));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        node.forEach(item -> list.add(item.asText()));
        return List.copyOf(list);
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        return map;
    }
}
