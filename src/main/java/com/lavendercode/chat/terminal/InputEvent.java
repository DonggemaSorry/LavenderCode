package com.lavendercode.chat.terminal;

public sealed interface InputEvent {

    record SendMessage(String text) implements InputEvent {}

    record ExecuteCommand(CommandType type, String args) implements InputEvent {}

    record Shutdown() implements InputEvent {}

    enum CommandType {
        EXIT, QUIT, CLEAR, HELP, THEME, CANCEL, SCROLL
    }
}
