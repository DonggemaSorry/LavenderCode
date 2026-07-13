package com.lavendercode.core.command;

import java.util.List;
import java.util.Objects;

public record CommandMetadata(
    String name,
    List<String> aliases,
    String description,
    CommandKind kind,
    boolean hidden
) {
    public CommandMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(kind, "kind");
        aliases = List.copyOf(aliases);
    }
}