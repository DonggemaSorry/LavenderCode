package com.lavendercode.core.worktree;

public record ExitOptions(boolean discardChanges) {
    public static ExitOptions keepSafe() {
        return new ExitOptions(false);
    }

    public static ExitOptions discard() {
        return new ExitOptions(true);
    }
}
