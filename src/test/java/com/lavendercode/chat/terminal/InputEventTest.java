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

        var event2 = new InputEvent.ExecuteCommand(CommandType.CLEAR, "");
        assertThat(event2.args()).isEmpty();
    }

    @Test
    void shouldCreateShutdown() {
        assertThat(new InputEvent.Shutdown()).isInstanceOf(InputEvent.class);
    }

    @Test
    void shouldCreateResumeSession() {
        var event = new InputEvent.ResumeSession("20260713-101500-a1b2c3d4");
        assertThat(event.sessionId()).isEqualTo("20260713-101500-a1b2c3d4");
        assertThat(event).isInstanceOf(InputEvent.class);
    }

    @Test
    void shouldSupportPatternMatching() {
        InputEvent send    = new InputEvent.SendMessage("x");
        InputEvent cmd     = new InputEvent.ExecuteCommand(CommandType.EXIT, "x");
        InputEvent shutdown = new InputEvent.Shutdown();
        InputEvent resume = new InputEvent.ResumeSession("sid");

        assertThat(switch (send) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("msg:x");

        assertThat(switch (cmd) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("cmd:EXIT");

        assertThat(switch (shutdown) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("shutdown");

        assertThat(switch (resume) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("resume:sid");
    }
}
