package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Options(
    @JsonProperty("max_tokens")
    int maxTokens,

    @JsonProperty("system_prompt")
    String systemPrompt,

    @JsonProperty("tool_system_enabled")
    Boolean toolSystemEnabled,

    @JsonProperty("command_execution_enabled")
    boolean commandExecutionEnabled,

    @JsonProperty("command_timeout_seconds")
    int commandTimeoutSeconds,

    @JsonProperty("file_operation_timeout_seconds")
    int fileOperationTimeoutSeconds,

    @JsonProperty("read_file_max_lines")
    int readFileMaxLines,

    @JsonProperty("command_output_max_chars")
    int commandOutputMaxChars,

    @JsonProperty("search_max_results")
    int searchMaxResults,

    @JsonProperty("enable_sub_agent_background")
    Boolean enableSubAgentBackground
) {
    public Options() {
        this(4096, "", true, false, 120, 30, 2000, 30000, 200, true);
    }

    public Options(int maxTokens, String systemPrompt) {
        this(maxTokens, systemPrompt, true, false, 120, 30, 2000, 30000, 200, true);
    }

    public Options {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        if (toolSystemEnabled == null) {
            toolSystemEnabled = true;
        }
        if (enableSubAgentBackground == null) {
            enableSubAgentBackground = true;
        }
    }

    /** Returns a copy with a different system prompt. */
    public Options withSystemPrompt(String newSystemPrompt) {
        return new Options(maxTokens, newSystemPrompt, toolSystemEnabled,
            commandExecutionEnabled, commandTimeoutSeconds,
            fileOperationTimeoutSeconds, readFileMaxLines,
            commandOutputMaxChars, searchMaxResults, enableSubAgentBackground);
    }
}
