package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuiltinCommandRegistryTest {
    @Test
    void compactMapsToCompactType() {
        BuiltinCommandRegistry.ParseResult r = BuiltinCommandRegistry.parse("/compact");
        assertThat(r.type()).isEqualTo(InputEvent.CommandType.COMPACT);
    }

    @Test
    void unknownCommandReturnsUnknownWithHint() {
        BuiltinCommandRegistry.ParseResult r = BuiltinCommandRegistry.parse("/foo");
        assertThat(r.type()).isEqualTo(InputEvent.CommandType.UNKNOWN);
        assertThat(r.hint()).contains("/compact");
    }

    @Test
    void helpTextListsCompactCommand() {
        assertThat(BuiltinCommandRegistry.helpText()).contains("/compact");
    }

    @Test
    void bareSlashReturnsHelp() {
        BuiltinCommandRegistry.ParseResult r = BuiltinCommandRegistry.parse("/");
        assertThat(r.type()).isEqualTo(InputEvent.CommandType.HELP);
    }
}
