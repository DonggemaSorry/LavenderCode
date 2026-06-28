package com.lavendercode.core.tool;

public class ToolTimeoutException extends RuntimeException {
    public ToolTimeoutException(String toolName, long timeoutSeconds) {
        super("Tool '" + toolName + "' timed out after " + timeoutSeconds + " seconds");
    }
}
