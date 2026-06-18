package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import com.lavendercode.chat.terminal.InputEvent.CommandType;

class InputEventTest {

    @Test
    void shouldCreateSendMessage() {
        var event = new InputEvent.SendMessage("Hello");
        assertThat(event.text()).isEqualTo("Hello");
        assertThat(event).isInstanceOf(InputEvent.class);
    }

    @Test
    void shouldCreateExecuteCommand() {
        var event = new InputEvent.ExecuteCommand(CommandType.HELP, "");
        assertThat(event.type()).isEqualTo(CommandType.HELP);
        assertThat(event.args()).isEmpty();
    }

    @Test
    void shouldCreateShutdown() {
        assertThat(new InputEvent.Shutdown()).isNotNull();
    }

    @Test
    void shouldSupportPatternMatching() {
        InputEvent send    = new InputEvent.SendMessage("x");
        InputEvent cmd     = new InputEvent.ExecuteCommand(CommandType.EXIT, "x");
        InputEvent shutdown = new InputEvent.Shutdown();

        String result = switch (send) {
            case InputEvent.SendMessage(var t)  -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.Shutdown()          -> "shutdown";
        };
        assertThat(result).isEqualTo("msg:x");
    }
}
