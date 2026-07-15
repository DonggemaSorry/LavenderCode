package com.lavendercode.core.team;

/** 队员侧 Plan 审批状态（同线程，供权限/prompt 读取）。 */
public final class PlanApprovalState {
    private static final ThreadLocal<Boolean> APPROVED = new ThreadLocal<>();
    private static final ThreadLocal<String> FEEDBACK = new ThreadLocal<>();

    private PlanApprovalState() {}

    public static void markApproved() {
        APPROVED.set(true);
        FEEDBACK.remove();
    }

    public static void markRejected(String feedback) {
        APPROVED.set(false);
        FEEDBACK.set(feedback);
    }

    public static boolean consumeApproved() {
        Boolean v = APPROVED.get();
        APPROVED.remove();
        return Boolean.TRUE.equals(v);
    }

    public static String consumeFeedback() {
        String f = FEEDBACK.get();
        FEEDBACK.remove();
        return f;
    }

    public static void clear() {
        APPROVED.remove();
        FEEDBACK.remove();
    }
}
