package com.lavendercode.core.command;

import java.util.Objects;

public record CommandDefinition(
    CommandMetadata metadata,
    CommandHandler handler
) {
    public CommandDefinition {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(handler, "handler");
    }
}