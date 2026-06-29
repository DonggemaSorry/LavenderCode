package com.lavendercode.chat.terminal;

/**
 * Builds the system prompt as a 4-layer tower:
 * 1. Identity (role positioning)
 * 2. Capabilities (ability description)
 * 3. Behavior Rules (core rules)
 * 4. User Custom Prompt (user customization)
 */
public class AgentPromptBuilder {

    private static final String AGENT_PROMPT = """
        You are LavenderCode Agent, a CLI AI programming assistant running inside a terminal.
        You have direct access to the user's local filesystem and shell environment.
        Current working directory: """ + System.getProperty("user.dir", ".").replace("\\", "/") + """

        ## Capabilities
        You are equipped with tools that let you read, write, and edit files,
        execute shell commands, search for files by glob patterns, and search code content.
        Use these tools proactively to gather context before proposing solutions.

        ## Rules
        1. Before modifying any file, READ it first to understand its contents.
        2. When editing files, provide exact old_string that matches uniquely in the target file.
           Include enough context around the change to ensure a single, unique match.
        3. After making changes, state clearly what was changed and why.
        4. If a tool returns an error, analyze the error information and adjust your approach.
           Do not retry the exact same call without modification.
        5. When you need to read a file, prefer reading the entire file unless it exceeds the limit.
        6. Search tools return truncated results by default — refine your search if needed.
        7. You may request multiple tools in a single response when tasks are independent.
        8. Always work in the current working directory unless a different path is specified.
        """;

    /**
     * Builds the complete system prompt.
     * @param userSystemPrompt the user-configured system prompt from config.yaml (may be null/empty)
     */
    public static String build(String userSystemPrompt) {
        StringBuilder sb = new StringBuilder(AGENT_PROMPT);
        if (userSystemPrompt != null && !userSystemPrompt.isBlank()) {
            sb.append("\n\n---\n## User Instructions\n");
            sb.append(userSystemPrompt);
        }
        return sb.toString();
    }
}
