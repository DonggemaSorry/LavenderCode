package com.lavendercode.core.subagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lavendercode.core.permission.PermissionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class AgentDefinitionParser {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9-]{1,32}$");
    private static final Set<String> VALID_MODELS = Set.of("haiku", "sonnet", "opus", "inherit");

    private AgentDefinitionParser() {}

    public static AgentDefinition parse(String content, AgentCatalog.Source source) {
        String frontmatter = null;
        String body = content;
        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            int end = content.indexOf("\n---", 4);
            if (end > 0) {
                frontmatter = content.substring(4, end);
                int bodyStart = end + 4;
                while (bodyStart < content.length()
                    && (content.charAt(bodyStart) == '\n' || content.charAt(bodyStart) == '\r')) {
                    bodyStart++;
                }
                body = content.substring(bodyStart);
            }
        }
        if (frontmatter == null) {
            throw new ParseException("Agent definition must start with YAML frontmatter");
        }
        try {
            JsonNode node = YAML.readTree(frontmatter);
            String name = requiredText(node, "name");
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new ParseException("Invalid agent name: " + name);
            }
            String description = requiredText(node, "description");
            List<String> tools = readStringList(node.get("tools"));
            List<String> disallowedTools = readStringList(node.get("disallowedTools"));
            String model = resolveModel(text(node, "model"));
            int maxTurns = node.has("maxTurns") ? node.get("maxTurns").asInt() : AgentDefinition.DEFAULT_MAX_TURNS;
            PermissionMode permissionMode = resolvePermissionMode(text(node, "permissionMode"));
            boolean background = node.has("background") && node.get("background").asBoolean(false);
            String isolation = resolveIsolation(text(node, "isolation"));
            String systemPrompt = body.strip();
            return new AgentDefinition(
                name, description, tools, disallowedTools, model, maxTurns,
                permissionMode, background, isolation, systemPrompt, source);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse agent frontmatter: " + e.getMessage(), e);
        }
    }

    private static String resolveIsolation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if ("worktree".equals(trimmed)) {
            return "worktree";
        }
        System.err.println("WARN: unknown agent isolation '" + trimmed + "', fallback to empty");
        return "";
    }

    private static String resolveModel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "inherit";
        }
        String trimmed = raw.trim();
        if (!VALID_MODELS.contains(trimmed)) {
            System.err.println("WARN: unknown agent model '" + trimmed + "', fallback to inherit");
            return "inherit";
        }
        return trimmed;
    }

    private static PermissionMode resolvePermissionMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PermissionMode.DEFAULT;
        }
        String trimmed = raw.trim();
        return switch (trimmed) {
            case "acceptEdits" -> PermissionMode.ACCEPT_EDITS;
            case "plan" -> PermissionMode.PLAN;
            case "bypassPermissions" -> PermissionMode.BYPASS_PERMISSIONS;
            case "default" -> PermissionMode.DEFAULT;
            case "dontAsk" -> PermissionMode.DONT_ASK;
            default -> {
                System.err.println("WARN: unknown permissionMode '" + trimmed + "', fallback to default");
                yield PermissionMode.DEFAULT;
            }
        };
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new ParseException("Missing required field: " + field);
        }
        return value.trim();
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

    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
