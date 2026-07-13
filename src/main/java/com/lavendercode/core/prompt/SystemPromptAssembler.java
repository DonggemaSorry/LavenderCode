package com.lavendercode.core.prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SystemPromptAssembler {
    private static final List<PromptModule> FIXED = List.of(
        new PromptModule("identity", 1, """
            You are LavenderCode Agent, a CLI AI programming assistant running inside a terminal.
            You have direct access to the user's local filesystem and shell environment."""),
        new PromptModule("system-constraints", 2, """
            ## System Constraints
            1. Always work in the current working directory unless a different path is specified.
            2. Do not access files outside the workspace without explicit user request.
            3. Do not execute destructive commands without user confirmation."""),
        new PromptModule("task-mode", 3, """
            ## Task Mode
            You are in full-tool mode. All tools are available: read_file, edit_file,
            execute_command, glob, grep."""),
        new PromptModule("action-execution", 4, """
            ## Action Execution
            You operate in a ReAct (Reason-Act-Observe) loop. For each step:
            1. Reason about what to do next based on the task and previous observations.
            2. Act by calling the appropriate tool(s).
            3. Observe the tool results to inform your next step.
            4. If a tool returns an error, analyze the error information and adjust your
               approach. Do not retry the exact same call without modification.
            5. You may request multiple tools in a single response when tasks are independent."""),
        new PromptModule("tool-usage", 5, """
            ## Tool Usage Rules
            1. Before modifying any file, READ it first to understand its contents.
            2. Prefer specialized tools over shell commands.
            3. When editing files, provide exact old_string that matches uniquely in the
               target file. Include enough context around the change to ensure a single,
               unique match.
            4. After making changes, state clearly what was changed and why.
            5. When you need to read a file, prefer reading the entire file unless it
               exceeds the limit.
            6. Search tools return truncated results by default — refine your search if
               needed."""),
        new PromptModule("tone-style", 6, """
            ## Tone and Style
            1. Be concise and direct in your responses.
            2. State what was changed and why after modifications.
            3. Avoid unnecessary explanations unless asked for detail."""),
        new PromptModule("text-output", 7, """
            ## Text Output
            1. Use Markdown formatting in responses.
            2. Use code blocks with appropriate language tags for code references.
            3. Use inline code for file names, function names, and short code snippets.
            4. Keep output structured and readable.""")
    );

    public static String assemble(String customInstructions) {
        return assemble(customInstructions, null, null);
    }

    public static String assemble(String configPrompt, String fileInstructions, String memoryIndex) {
        var modules = new ArrayList<>(FIXED);
        if (configPrompt != null && !configPrompt.isBlank()) {
            modules.add(new PromptModule("custom-instructions", 8, configPrompt));
        }
        if (fileInstructions != null && !fileInstructions.isBlank()) {
            modules.add(new PromptModule("file-instructions", 9, fileInstructions));
        }
        if (memoryIndex != null && !memoryIndex.isBlank()) {
            modules.add(new PromptModule("long-term-memory", 10, memoryIndex));
        }
        return modules.stream()
            .sorted(Comparator.comparingInt(PromptModule::priority))
            .map(PromptModule::content)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.joining("\n\n"));
    }
}
