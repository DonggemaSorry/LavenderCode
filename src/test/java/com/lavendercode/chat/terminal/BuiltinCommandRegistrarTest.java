package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BuiltinCommandRegistrarTest {

    @Test
    void returnsTwelveCommandDefinitions() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        assertThat(defs).hasSize(12);
    }

    @Test
    void allNamesMatchExpectedSet() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var names = defs.stream().map(d -> d.metadata().name()).toList();
        assertThat(names).containsExactlyInAnyOrder(
            "clear", "compact", "do", "exit", "help", "memory",
            "permission", "plan", "resume", "review", "session", "status"
        );
    }

    @Test
    void exitHasQuitAlias() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var exitDef = defs.stream()
            .filter(d -> d.metadata().name().equals("exit"))
            .findFirst().orElseThrow();
        assertThat(exitDef.metadata().aliases()).containsExactly("quit");
    }

    @Test
    void noOtherCommandHasAliases() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var withAliases = defs.stream()
            .filter(d -> !d.metadata().aliases().isEmpty())
            .toList();
        assertThat(withAliases).hasSize(1);
        assertThat(withAliases.get(0).metadata().name()).isEqualTo("exit");
    }

    @Test
    void allCommandsAreNotHidden() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        assertThat(defs).allMatch(d -> !d.metadata().hidden());
    }

    @Test
    void localCommandsAreStatusMemoryPermissionSessionHelp() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var localNames = defs.stream()
            .filter(d -> d.metadata().kind() == CommandKind.LOCAL)
            .map(d -> d.metadata().name())
            .toList();
        assertThat(localNames).containsExactlyInAnyOrder(
            "help", "memory", "permission", "session", "status"
        );
    }

    @Test
    void uiCommandsAreClearCompactExitPlanResume() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var uiNames = defs.stream()
            .filter(d -> d.metadata().kind() == CommandKind.UI)
            .map(d -> d.metadata().name())
            .toList();
        assertThat(uiNames).containsExactlyInAnyOrder(
            "clear", "compact", "exit", "plan", "resume"
        );
    }

    @Test
    void promptCommandsAreDoReview() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var promptNames = defs.stream()
            .filter(d -> d.metadata().kind() == CommandKind.PROMPT)
            .map(d -> d.metadata().name())
            .toList();
        assertThat(promptNames).containsExactlyInAnyOrder("do", "review");
    }

    @Test
    void canConstructRegistryWithoutConflict() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var registry = new CommandRegistry(defs);
        assertThat(registry.visibleCommands()).hasSize(12);
    }

    @Test
    void helpTextContainsAllCommands() {
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var registry = new CommandRegistry(defs);
        BuiltinCommandRegistrar.bindRegistry(registry);
        String help = BuiltinCommandRegistrar.formatHelp();
        assertThat(help).contains("/exit", "/clear", "/help", "/compact", "/plan", "/do");
        assertThat(help).contains("/memory", "/permission", "/resume", "/review", "/session", "/status");
    }
}
