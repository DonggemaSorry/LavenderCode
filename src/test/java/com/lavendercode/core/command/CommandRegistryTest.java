package com.lavendercode.core.command;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CommandRegistryTest {

    private static CommandDefinition def(String name, List<String> aliases, CommandKind kind) {
        return new CommandDefinition(
            new CommandMetadata(name, aliases, "desc", kind, false), ctx -> {});
    }

    @Test
    void findByNameReturnsDefinition() {
        var registry = new CommandRegistry(List.of(
            def("exit", List.of("quit"), CommandKind.UI)
        ));
        assertThat(registry.find("exit")).isPresent();
    }

    @Test
    void findByAliasReturnsDefinition() {
        var registry = new CommandRegistry(List.of(
            def("exit", List.of("quit"), CommandKind.UI)
        ));
        assertThat(registry.find("quit")).isPresent();
    }

    @Test
    void findIsCaseInsensitive() {
        var registry = new CommandRegistry(List.of(
            def("exit", List.of(), CommandKind.UI)
        ));
        assertThat(registry.find("EXIT")).isPresent();
        assertThat(registry.find("Exit")).isPresent();
    }

    @Test
    void findReturnsEmptyForUnknown() {
        var registry = new CommandRegistry(List.of(
            def("exit", List.of(), CommandKind.UI)
        ));
        assertThat(registry.find("unknown")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void duplicateNameThrowsAtConstruction() {
        assertThatThrownBy(() -> new CommandRegistry(List.of(
            def("exit", List.of(), CommandKind.UI),
            def("exit", List.of(), CommandKind.UI)
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit");
    }

    @Test
    void duplicateAliasThrowsAtConstruction() {
        var def1 = new CommandDefinition(
            new CommandMetadata("exit", List.of("quit"), "退出", CommandKind.UI, false), ctx -> {});
        var def2 = new CommandDefinition(
            new CommandMetadata("leave", List.of("quit"), "离开", CommandKind.UI, false), ctx -> {});
        assertThatThrownBy(() -> new CommandRegistry(List.of(def1, def2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("quit");
    }

    @Test
    void nameAndAliasCrossCollisionThrows() {
        var def1 = new CommandDefinition(
            new CommandMetadata("help", List.of(), "帮助", CommandKind.LOCAL, false), ctx -> {});
        var def2 = new CommandDefinition(
            new CommandMetadata("assist", List.of("help"), "辅助", CommandKind.LOCAL, false), ctx -> {});
        assertThatThrownBy(() -> new CommandRegistry(List.of(def1, def2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("help");
    }

    @Test
    void caseInsensitiveConflictDetected() {
        var def1 = new CommandDefinition(
            new CommandMetadata("Exit", List.of(), "退出", CommandKind.UI, false), ctx -> {});
        var def2 = new CommandDefinition(
            new CommandMetadata("exit", List.of(), "重复", CommandKind.UI, false), ctx -> {});
        assertThatThrownBy(() -> new CommandRegistry(List.of(def1, def2)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void visibleCommandsSortedByName() {
        var registry = new CommandRegistry(List.of(
            def("zebra", List.of(), CommandKind.LOCAL),
            def("alpha", List.of(), CommandKind.LOCAL),
            def("middle", List.of(), CommandKind.LOCAL)
        ));
        var visible = registry.visibleCommands();
        assertThat(visible).extracting(d -> d.metadata().name())
            .containsExactly("alpha", "middle", "zebra");
    }

    @Test
    void hiddenCommandsExcludedFromVisibleButInAll() {
        var hidden = new CommandDefinition(
            new CommandMetadata("secret", List.of(), "hidden", CommandKind.LOCAL, true), ctx -> {});
        var visible = new CommandDefinition(
            new CommandMetadata("exit", List.of(), "退出", CommandKind.UI, false), ctx -> {});
        var registry = new CommandRegistry(List.of(hidden, visible));
        assertThat(registry.visibleCommands()).hasSize(1);
        assertThat(registry.allCommands()).hasSize(2);
    }
}
