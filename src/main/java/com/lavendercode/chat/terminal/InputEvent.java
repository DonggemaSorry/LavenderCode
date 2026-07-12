package com.lavendercode.chat.terminal;

public sealed interface InputEvent {

    record SendMessage(String text) implements InputEvent {}

    record ExecuteCommand(CommandType type, String args) implements InputEvent {}

    record Shutdown() implements InputEvent {}

    record CyclePermissionMode() implements InputEvent {}

    record HitlChoice(com.lavendercode.core.permission.HitlChoice choice) implements InputEvent {}

    enum CommandType {
        EXIT, QUIT, CLEAR, HELP, CANCEL, SCROLL, ESC_CANCEL, PLAN, DO, COMPACT, UNKNOWN
    }
}
