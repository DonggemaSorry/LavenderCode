package com.lavendercode.core.context;

public final class ContextWindowDefaults {
    private ContextWindowDefaults() {}

    public static int resolve(String protocol, Integer configured) {
        if (configured != null) return configured;
        return switch (protocol.toLowerCase()) {
            case "anthropic" -> 200_000;
            case "openai" -> 128_000;
            default -> 128_000;
        };
    }

    public static int autoCompactThreshold(int contextWindow) {
        return contextWindow - ContextConstants.SUMMARY_OUTPUT_RESERVE - ContextConstants.AUTO_SAFETY_MARGIN;
    }

    public static int manualSummaryInputLimit(int contextWindow) {
        return contextWindow - ContextConstants.SUMMARY_OUTPUT_RESERVE - ContextConstants.MANUAL_SAFETY_MARGIN;
    }
}
