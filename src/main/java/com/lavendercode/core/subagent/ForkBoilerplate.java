package com.lavendercode.core.subagent;

public final class ForkBoilerplate {

    public static final String TAG = "<fork_boilerplate>";

    public static final String TEMPLATE = """
        <fork_boilerplate>
        You are a forked sub-agent. Rules:
        - Do NOT launch another Agent (no nested forks).
        - Do NOT ask the user for confirmation; use tools directly.
        - Stay within the assigned scope.
        - Complete the task using available tools.

        Scope: %s

        Task:
        %s
        """;

    private ForkBoilerplate() {}

    public static String format(String prompt) {
        String scope = prompt.length() > 500 ? prompt.substring(0, 500) : prompt;
        return String.format(TEMPLATE, scope, prompt);
    }
}
