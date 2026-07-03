package com.lavendercode.core.prompt;

import java.util.Optional;

public class ReminderInjector {
    private static final int REPEAT_INTERVAL = 5;

    private static final String PLAN_FULL = """
        <system-reminder>
        You are in PLAN MODE. Only read-only tools are available
        (read_file, glob, grep). DO NOT attempt to write, edit, or execute commands.
        Your goal is to explore the codebase and produce a clear, actionable plan.
        After exploring, provide a step-by-step plan describing what files to
        read/modify and what commands to run. The user will switch to /do to execute.
        </system-reminder>""";

    private static final String PLAN_BRIEF =
        "<system-reminder>Plan mode: read-only tools only. Produce an actionable plan.</system-reminder>";

    public static Optional<String> inject(int round, boolean planMode) {
        if (!planMode) return Optional.empty();
        boolean firstRound = (round == 1);
        boolean repeatRound = (round % REPEAT_INTERVAL == 0);
        return Optional.of(firstRound || repeatRound ? PLAN_FULL : PLAN_BRIEF);
    }
}
