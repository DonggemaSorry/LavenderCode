package com.lavendercode.core.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lavendercode.core.permission.MatchType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class HookConfigLoader {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public HookConfig load(Path projectRoot) {
        Path userHome = Path.of(System.getProperty("user.home"));
        return load(projectRoot, userHome);
    }

    public HookConfig load(Path projectRoot, Path userHome) {
        Path projectFile = projectRoot.resolve(".lavendercode/hooks.yaml");
        Path userFile    = userHome.resolve(".lavendercode/hooks.yaml");

        List<HookRule> projectRules = loadFile(projectFile, "project");
        List<HookRule> userRules    = loadFile(userFile, "user");

        List<String> sources = new ArrayList<>();
        if (Files.exists(projectFile)) sources.add(projectFile.toString());
        if (Files.exists(userFile))    sources.add(userFile.toString());

        // Merge: project first, then user; duplicate name → skip later, warn
        Set<String> seen = new LinkedHashSet<>();
        List<HookRule> merged = new ArrayList<>();
        for (HookRule r : projectRules) {
            if (seen.add(r.name())) merged.add(r);
        }
        for (HookRule r : userRules) {
            if (!seen.add(r.name())) {
                System.err.println("[hook] duplicate name skipped: " + r.name());
            } else {
                merged.add(r);
            }
        }

        return new HookConfig(merged, sources);
    }

    // ── File-level parsing ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<HookRule> loadFile(Path file, String layer) {
        if (!Files.exists(file)) return List.of();

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> root = mapper.readValue(file.toFile(), Map.class);
            if (root == null) return List.of();

            Object hooksObj = root.get("hooks");
            if (!(hooksObj instanceof List<?> hooksList)) return List.of();

            List<HookRule> rules = new ArrayList<>();
            for (Object item : hooksList) {
                if (!(item instanceof Map<?, ?>)) continue;
                HookRule rule = parseRule((Map<String, Object>) item);
                if (rule != null) rules.add(rule);
            }
            return rules;
        } catch (Exception e) {
            System.err.println("[hook] invalid YAML in " + layer + ": " + e.getMessage());
            return List.of();
        }
    }

    // ── Rule parsing ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HookRule parseRule(Map<String, Object> map) {
        // name (required)
        Object nameObj = map.get("name");
        if (!(nameObj instanceof String name) || name.isBlank()) {
            System.err.println("[hook] missing name, skip rule");
            return null;
        }

        // event (required)
        Object eventObj = map.get("event");
        if (!(eventObj instanceof String eventName)) {
            System.err.println("[hook] missing event for rule: " + name + ", skip");
            return null;
        }
        HookEvent event;
        try {
            event = HookEvent.fromName(eventName);
        } catch (IllegalArgumentException e) {
            System.err.println("[hook] unknown event: " + eventName + ", skip rule: " + name);
            return null;
        }

        // condition (optional "if:" block)
        HookCondition condition = null;
        Object ifObj = map.get("if");
        if (ifObj instanceof Map<?, ?> rawIfMap) {
            Map<String, Object> ifMap = (Map<String, Object>) rawIfMap;
            boolean hasAllOf = ifMap.containsKey("all_of");
            boolean hasAnyOf = ifMap.containsKey("any_of");
            if (hasAllOf && hasAnyOf) {
                System.err.println("[hook] both all_of and any_of present, skip rule: " + name);
                return null;
            }
            condition = parseCondition(ifMap);
        }

        // action
        HookAction action = null;
        Object actionObj = map.get("action");
        if (actionObj instanceof Map<?, ?>) {
            action = parseAction((Map<String, Object>) actionObj);
        }

        // only_once
        boolean onlyOnce = Boolean.TRUE.equals(map.get("only_once"));

        // async — reject on interceptable events
        boolean async = Boolean.TRUE.equals(map.get("async"));
        if (async && event.interceptable()) {
            System.err.println("[hook] async=true on interceptable event " + event
                    + ", skip rule: " + name);
            return null;
        }

        // timeout
        Duration timeout = parseTimeout(map.get("timeout"));

        return new HookRule(name, event, condition, action, onlyOnce, async, timeout);
    }

    // ── Condition parsing ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HookCondition parseCondition(Map<String, Object> ifMap) {
        if (ifMap.containsKey("all_of")) {
            List<HookCondition.Atom> atoms = parseAtoms((List<?>) ifMap.get("all_of"));
            return new HookCondition.AllOf(atoms);
        }
        if (ifMap.containsKey("any_of")) {
            List<HookCondition.Atom> atoms = parseAtoms((List<?>) ifMap.get("any_of"));
            return new HookCondition.AnyOf(atoms);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<HookCondition.Atom> parseAtoms(List<?> list) {
        if (list == null) return List.of();
        List<HookCondition.Atom> atoms = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?>)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            String field = (String) m.get("field");
            MatchType match = parseMatch((Map<String, Object>) m.get("match"));
            atoms.add(new HookCondition.Atom(field, match));
        }
        return Collections.unmodifiableList(atoms);
    }

    @SuppressWarnings("unchecked")
    private MatchType parseMatch(Map<String, Object> matchMap) {
        if (matchMap == null) return new MatchType.Exact("");

        // not: { ...inner match... }
        if (matchMap.containsKey("not")) {
            Object notVal = matchMap.get("not");
            if (notVal instanceof Map<?, ?>) {
                return new MatchType.Not(parseMatch((Map<String, Object>) notVal));
            }
        }

        String type  = (String) matchMap.getOrDefault("type", "exact");
        String value = (String) matchMap.getOrDefault("value", "");
        return switch (type) {
            case "exact" -> new MatchType.Exact(value);
            case "glob"  -> new MatchType.Glob(value);
            case "regex" -> new MatchType.Regex(value);
            default      -> new MatchType.Exact(value);
        };
    }

    // ── Action parsing ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HookAction parseAction(Map<String, Object> actionMap) {
        Object typeObj = actionMap.get("type");
        if (!(typeObj instanceof String type)) return null;

        return switch (type) {
            case "shell" -> new HookAction.Shell((String) actionMap.get("command"));
            case "prompt" -> new HookAction.Prompt((String) actionMap.get("text"));
            case "http" -> {
                Map<String, String> headers = new LinkedHashMap<>();
                Object hObj = actionMap.get("headers");
                if (hObj instanceof Map<?, ?> hMap) {
                    hMap.forEach((k, v) -> headers.put(k.toString(), v.toString()));
                }
                yield new HookAction.Http(
                        (String) actionMap.get("url"),
                        (String) actionMap.getOrDefault("method", "POST"),
                        headers,
                        (String) actionMap.get("body")
                );
            }
            case "subagent" -> new HookAction.Subagent(
                    (String) actionMap.get("agent_name"),
                    (String) actionMap.get("prompt")
            );
            default -> null;
        };
    }

    // ── Timeout parsing ──────────────────────────────────────────────────────

    private Duration parseTimeout(Object timeoutObj) {
        if (timeoutObj == null) return DEFAULT_TIMEOUT;
        String s = timeoutObj.toString().trim();

        if (s.endsWith("s")) {
            try {
                long n = Long.parseLong(s.substring(0, s.length() - 1));
                return Duration.ofSeconds(n);
            } catch (NumberFormatException ignored) { /* fall through */ }
        } else if (s.endsWith("m")) {
            try {
                long n = Long.parseLong(s.substring(0, s.length() - 1));
                return Duration.ofMinutes(n);
            } catch (NumberFormatException ignored) { /* fall through */ }
        }

        return DEFAULT_TIMEOUT;
    }
}
