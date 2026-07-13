package com.lavendercode.chat.terminal;

public final class ResumeGate {
    private ResumeGate() {
    }

    public static String check(boolean agentRunning, boolean resuming) {
        if (agentRunning) {
            return "请等待当前任务完成";
        }
        if (resuming) {
            return "恢复中";
        }
        return null;
    }
}
