package com.lavendercode.core.context;

public class CompactionException extends RuntimeException {
    public CompactionException(String message) {
        super(message);
    }

    public CompactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
