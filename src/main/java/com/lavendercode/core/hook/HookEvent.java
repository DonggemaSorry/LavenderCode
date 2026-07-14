package com.lavendercode.core.hook;

public enum HookEvent {
    SessionStart, SessionEnd, SessionResume,
    UserPromptSubmit, Stop, PreUserMessage,
    PreToolUse, PostToolUse,
    PreCompact, PostCompact, Notification;

    public boolean interceptable() {
        return this == PreToolUse || this == UserPromptSubmit;
    }

    public static HookEvent fromName(String name) {
        try {
            return HookEvent.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown event: " + name, e);
        }
    }
}
