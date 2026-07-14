package com.lavendercode.core.skill;

import java.nio.file.Path;
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
}
