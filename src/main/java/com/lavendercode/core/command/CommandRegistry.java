package com.lavendercode.core.command;

import java.util.*;

public final class CommandRegistry {
    private final Map<String, CommandDefinition> lookup;
    private final List<CommandDefinition> all;

    public CommandRegistry(List<CommandDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, CommandDefinition> map = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (var def : definitions) {
            String name = def.metadata().name().toLowerCase();
            if (!seen.add(name)) {
                throw new IllegalStateException(
                    "命令名冲突: '" + name + "' 已被注册");
            }
            map.put(name, def);
            for (String alias : def.metadata().aliases()) {
                String lower = alias.toLowerCase();
                if (!seen.add(lower)) {
                    throw new IllegalStateException(
                        "命令别名冲突: '" + lower + "' 已被注册");
                }
                map.put(lower, def);
            }
        }

        this.lookup = Map.copyOf(map);
        this.all = List.copyOf(definitions);
    }

    public Optional<CommandDefinition> find(String nameOrAlias) {
        if (nameOrAlias == null) return Optional.empty();
        return Optional.ofNullable(lookup.get(nameOrAlias.toLowerCase()));
    }

    public List<CommandDefinition> visibleCommands() {
        return all.stream()
            .filter(d -> !d.metadata().hidden())
            .sorted(Comparator.comparing(d -> d.metadata().name()))
            .toList();
    }

    public List<CommandDefinition> allCommands() {
        return all;
    }
}
