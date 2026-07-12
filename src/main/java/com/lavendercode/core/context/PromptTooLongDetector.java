package com.lavendercode.core.context;

public final class PromptTooLongDetector {
    private PromptTooLongDetector() {}

    public static boolean isPromptTooLong(String message, int statusCode) {
        if (statusCode == 413) return true;
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("prompt_too_long")
            || m.contains("context_length")
            || m.contains("maximum context length")
            || m.contains("too many tokens");
    }
}
