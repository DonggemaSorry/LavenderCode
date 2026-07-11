package com.lavendercode.core.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class PermissionConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private PermissionConfigLoader() {}

    public static PermissionConfig load(Path projectRoot, Path userConfigDir) {
        TierConfig user = loadTier(userConfigDir.resolve("permissions.yaml"));
        TierConfig project = loadTier(projectRoot.resolve(".lavendercode/permissions.yaml"));
        TierConfig local = loadTier(projectRoot.resolve(".lavendercode/permissions.local.yaml"));

        PermissionMode defaultMode = PermissionMode.DEFAULT;
        if (user.defaultMode() != null) {
            defaultMode = user.defaultMode();
        }
        if (project.defaultMode() != null) {
            defaultMode = project.defaultMode();
        }
        if (local.defaultMode() != null) {
            defaultMode = local.defaultMode();
        }

        return new PermissionConfig(local.rules(), project.rules(), user.rules(), defaultMode);
    }

    private static TierConfig loadTier(Path path) {
        if (!Files.exists(path)) {
            return TierConfig.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(path.toFile());
            PermissionMode mode = null;
            if (root.hasNonNull("defaultMode")) {
                mode = PermissionMode.fromYaml(root.get("defaultMode").asText());
            }
            List<PermissionRule> rules = parseRules(root.get("rules"));
            return new TierConfig(rules, mode);
        } catch (IOException e) {
            System.err.println("WARN: failed to load permission config " + path + ": " + e.getMessage());
            return TierConfig.empty();
        }
    }

    private static List<PermissionRule> parseRules(JsonNode rulesNode) {
        List<PermissionRule> rules = new ArrayList<>();
        if (rulesNode == null || !rulesNode.isArray()) {
            return rules;
        }
        for (JsonNode entry : rulesNode) {
            if (entry.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    rules.add(parseRuleEntry(field.getKey(), field.getValue().asText()));
                }
            }
        }
        return rules;
    }

    private static PermissionRule parseRuleEntry(String ruleText, String effectText) {
        PermissionRule.Effect effect = "deny".equalsIgnoreCase(effectText)
            ? PermissionRule.Effect.DENY
            : PermissionRule.Effect.ALLOW;
        return PermissionRule.parse(ruleText, effect);
    }

    private record TierConfig(List<PermissionRule> rules, PermissionMode defaultMode) {
        static TierConfig empty() {
            return new TierConfig(List.of(), null);
        }
    }
}
