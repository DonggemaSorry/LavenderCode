package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputSystemTest {

    @Test
    void shouldStopAfterExitOrQuitCommands() {
        assertThat(InputSystem.shouldStopAfter(
            new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""))).isTrue();
        assertThat(InputSystem.shouldStopAfter(
            new InputEvent.ExecuteCommand(InputEvent.CommandType.QUIT, ""))).isTrue();
    }

    @Test
    void shouldNotStopAfterOtherCommands() {
        assertThat(InputSystem.shouldStopAfter(
            new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, ""))).isFalse();
        assertThat(InputSystem.shouldStopAfter(new InputEvent.SendMessage("hello"))).isFalse();
    }
}
