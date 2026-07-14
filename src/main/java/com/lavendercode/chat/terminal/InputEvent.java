package com.lavendercode.chat.terminal;

public sealed interface InputEvent {

    record SendMessage(String text) implements InputEvent {}

    record ExecuteCommand(String rawInput) implements InputEvent {}

    record ResumeSession(String sessionId) implements InputEvent {}

    record Shutdown() implements InputEvent {}

    record CyclePermissionMode() implements InputEvent {}

    record HitlChoice(com.lavendercode.core.permission.HitlChoice choice) implements InputEvent {}

    record CancelAgent() implements InputEvent {}

    record ScrollEvent(String command) implements InputEvent {}
}
