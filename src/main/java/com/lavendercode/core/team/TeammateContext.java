package com.lavendercode.core.team;

public record TeammateContext(String teamName, String memberName, String agentId, boolean isLead) {
    private static final ThreadLocal<TeammateContext> TL = new ThreadLocal<>();

    public static void set(TeammateContext c) {
        TL.set(c);
    }

    public static TeammateContext get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}
