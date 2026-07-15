package com.lavendercode.core.subagent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentCatalog {

    public enum Source {
        BUILTIN,
        USER,
        PROJECT,
        PLUGIN
    }

    private static final List<String> BUILTIN_FILES = List.of(
        "general-purpose.md", "explore.md", "plan.md");

    private final Map<String, AgentDefinition> byName = new LinkedHashMap<>();

    public void register(AgentDefinition def) {
        byName.put(def.name(), def);
    }

    public AgentDefinition resolve(String name) {
        return byName.get(name);
    }

    public List<AgentDefinition> list() {
        return new ArrayList<>(byName.values());
    }

    public void clear() {
        byName.clear();
    }

    public void loadBuiltinFromClasspath() {
        for (String file : BUILTIN_FILES) {
            String resourcePath = "/subagent/builtin/" + file;
            try (InputStream in = AgentCatalog.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new RuntimeException("Missing builtin agent resource: " + resourcePath);
                }
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                register(AgentDefinitionParser.parse(content, Source.BUILTIN));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load builtin agent: " + file, e);
            }
        }
    }

    public void loadFromDirectory(Path dir, Source source) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".md")).forEach(p -> {
                try {
                    register(AgentDefinitionParser.parse(Files.readString(p), source));
                } catch (Exception e) {
                    System.err.println("WARN: skip agent file " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("WARN: cannot read agents dir " + dir + ": " + e.getMessage());
        }
    }

    public void loadAll(Path workDir) {
        clear();
        loadBuiltinFromClasspath();
        loadFromDirectory(
            Path.of(System.getProperty("user.home")).resolve(".lavendercode/agents"),
            Source.USER);
        if (workDir != null) {
            loadFromDirectory(workDir.resolve(".lavendercode/agents"), Source.PROJECT);
        }
    }
}
