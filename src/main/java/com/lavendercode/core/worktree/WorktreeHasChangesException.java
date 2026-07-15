package com.lavendercode.core.worktree;

public class WorktreeHasChangesException extends RuntimeException {
    public WorktreeHasChangesException(String message) {
        super(message);
    }
}
