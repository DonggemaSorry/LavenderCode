package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InputEventTest {

    @Test
    void shouldCreateSendMessage() {
        var event = new InputEvent.SendMessage("Hello");
        assertThat(event.text()).isEqualTo("Hello");
        assertThat(event).isInstanceOf(InputEvent.class);
    }

    @Test
    void shouldCreateExecuteCommand() {
        var event = new InputEvent.ExecuteCommand("/help");
        assertThat(event.rawInput()).isEqualTo("/help");

        var event2 = new InputEvent.ExecuteCommand("/clear");
        assertThat(event2.rawInput()).isEqualTo("/clear");
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
        InputEvent cmd     = new InputEvent.ExecuteCommand("/exit");
        InputEvent shutdown = new InputEvent.Shutdown();
        InputEvent resume = new InputEvent.ResumeSession("sid");
        InputEvent cancel = new InputEvent.CancelAgent();
        InputEvent scroll = new InputEvent.ScrollEvent("up");

        assertThat(switch (send) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("msg:x");

        assertThat(switch (cmd) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("cmd:/exit");

        assertThat(switch (shutdown) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("shutdown");

        assertThat(switch (resume) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("resume:sid");

        assertThat(switch (cancel) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("cancel");

        assertThat(switch (scroll) {
            case InputEvent.SendMessage(var t) -> "msg:" + t;
            case InputEvent.ExecuteCommand(var raw) -> "cmd:" + raw;
            case InputEvent.ResumeSession(var sessionId) -> "resume:" + sessionId;
            case InputEvent.CyclePermissionMode __ -> "cycle";
            case InputEvent.HitlChoice(var choice) -> "hitl:" + choice;
            case InputEvent.CancelAgent __ -> "cancel";
            case InputEvent.ScrollEvent(var command) -> "scroll:" + command;
            case InputEvent.Shutdown() -> "shutdown";
        }).isEqualTo("scroll:up");
    }
}
