package com.lavendercode.core.context;

public record CompactResult(boolean success, int tokensBefore, int tokensAfter, String error) {
    public static CompactResult ok(int before, int after) {
        return new CompactResult(true, before, after, null);
    }

    public static CompactResult fail(int before, String error) {
        return new CompactResult(false, before, before, error);
    }
}
