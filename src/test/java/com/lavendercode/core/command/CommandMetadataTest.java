package com.lavendercode.core.command;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CommandMetadataTest {
    @Test
    void shouldStoreAllFields() {
        var meta = new CommandMetadata("exit", List.of("quit"), "退出", CommandKind.UI, false);
        assertThat(meta.name()).isEqualTo("exit");
        assertThat(meta.aliases()).containsExactly("quit");
        assertThat(meta.description()).isEqualTo("退出");
        assertThat(meta.kind()).isEqualTo(CommandKind.UI);
        assertThat(meta.hidden()).isFalse();
    }

    @Test
    void shouldCopyAliasesList() {
        var aliases = new java.util.ArrayList<>(List.of("quit"));
        var meta = new CommandMetadata("exit", aliases, "退出", CommandKind.UI, false);
        aliases.add("leave");
        assertThat(meta.aliases()).containsExactly("quit");
    }

    @Test
    void shouldRejectNullFields() {
        assertThatThrownBy(() -> new CommandMetadata(null, List.of(), "d", CommandKind.LOCAL, false))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void commandKindHasThreeValues() {
        assertThat(CommandKind.values())
            .containsExactly(CommandKind.LOCAL, CommandKind.UI, CommandKind.PROMPT);
    }
}