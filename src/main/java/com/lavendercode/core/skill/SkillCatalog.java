package com.lavendercode.core.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SkillCatalog {

    public record SkillMeta(
        String name,
        String description,
        String whenToUse,
        List<String> tags,
        List<String> allowedTools,
        String mode,
        String model,
        String forkContext
    ) {
        public static SkillMeta withDefaults(
                String rawName, String description, String whenToUse,
                List<String> tags, List<String> allowedTools,
                String mode, String model, String forkContext) {
            String resolvedName = normalizeName(rawName);
            String resolvedMode = resolveMode(mode);
            String resolvedForkContext = forkContext != null ? forkContext : "none";
            List<String> resolvedTags = tags != null ? tags : List.of();
            return new SkillMeta(
                resolvedName, description, whenToUse,
                resolvedTags, allowedTools,
                resolvedMode, model, resolvedForkContext
            );
        }

        private static String normalizeName(String raw) {
            if (raw == null || raw.isBlank()) return "unnamed";
            return raw.trim().toLowerCase().replace(' ', '-');
        }

        private static String resolveMode(String mode) {
            if (mode == null || mode.isBlank()) return "inline";
            return mode.trim();
        }
    }

    public record Skill(SkillMeta meta, String promptBody, Path sourceDir, boolean bodyLoaded) {
        public Skill withBody(String newBody) {
            return new Skill(meta, newBody, sourceDir, true);
        }
    }

    private final java.util.Map<String, Skill> skills = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Path> sources = new java.util.LinkedHashMap<>();

    public SkillCatalog() {}

    public void register(Skill skill) {
        String name = skill.meta().name();
        skills.put(name, skill);
        if (skill.sourceDir() != null) {
            sources.put(name, skill.sourceDir());
        }
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public List<SkillMeta> list() {
        return skills.values().stream()
            .map(Skill::meta)
            .toList();
    }

    public Path source(String name) {
        return sources.get(name);
    }

    public void reload() {
        skills.clear();
        sources.clear();
    }

    public String buildActiveContext() {
        if (skills.isEmpty()) return "";
        var sb = new StringBuilder();
        for (Skill skill : skills.values()) {
            SkillMeta m = skill.meta();
            sb.append("- ").append(m.name());
            if (m.description() != null) {
                sb.append(": ").append(m.description());
            }
            if (m.whenToUse() != null) {
                sb.append(" (").append(m.whenToUse()).append(")");
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    // --- parseSkillMD ---

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    static Skill parseSkillMD(Path dir) {
        Path skillMd = dir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) return null;
        try {
            String content = Files.readString(skillMd);
            return parseSkillMdContent(dir, content);
        } catch (IOException e) {
            System.err.println("WARN: 无法读取 SKILL.md: " + skillMd + " — " + e.getMessage());
            return null;
        }
    }

    static Skill parseSkillMdContent(Path dir, String content) {
        String frontmatter = null;
        String body = content;
        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            int end = content.indexOf("\n---", 4);
            if (end > 0) {
                frontmatter = content.substring(4, end);
                int bodyStart = end + 4;
                while (bodyStart < content.length()
                       && (content.charAt(bodyStart) == '\n'
                           || content.charAt(bodyStart) == '\r')) {
                    bodyStart++;
                }
                body = content.substring(bodyStart);
            }
        }
        String dirName = dir.getFileName().toString();
        if (frontmatter != null) {
            try {
                JsonNode node = YAML.readTree(frontmatter);
                String name = text(node, "name");
                String description = text(node, "description");
                String whenToUse = text(node, "whenToUse");
                List<String> tags = readStringList(node.get("tags"));
                List<String> allowedTools = node.has("allowedTools")
                    ? readStringList(node.get("allowedTools")) : null;
                String mode = text(node, "mode");
                if (mode == null) {
                    mode = text(node, "context");
                }
                String model = text(node, "model");
                String forkContext = text(node, "forkContext");
                if (description == null) {
                    description = firstNonHeadingLine(body);
                }
                var meta = SkillMeta.withDefaults(
                    name != null ? name : dirName,
                    description, whenToUse, tags, allowedTools, mode, model, forkContext);
                return new Skill(meta, body, dir, true);
            } catch (Exception e) {
                System.err.println("WARN: SKILL.md frontmatter 解析失败，降级处理: " + e.getMessage());
            }
        }
        String description = firstNonHeadingLine(body);
        var meta = SkillMeta.withDefaults(dirName, description, null, List.of(), null, null, null, null);
        return new Skill(meta, body, dir, true);
    }

    private static String firstNonHeadingLine(String body) {
        String[] lines = body.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            return trimmed;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        node.forEach(item -> list.add(item.asText()));
        return List.copyOf(list);
    }
}
