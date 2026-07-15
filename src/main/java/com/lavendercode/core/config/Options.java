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
    Boolean enableSubAgentBackground,

    @JsonProperty("coordinator_mode")
    Boolean coordinatorMode,

    @JsonProperty("fork_teammate")
    Boolean forkTeammate
) {
    public Options() {
        this(4096, "", true, false, 120, 30, 2000, 30000, 200, true, false, false);
    }

    public Options(int maxTokens, String systemPrompt) {
        this(maxTokens, systemPrompt, true, false, 120, 30, 2000, 30000, 200, true, false, false);
    }

    /** 兼容旧 10 参构造。 */
    public Options(
            int maxTokens,
            String systemPrompt,
            Boolean toolSystemEnabled,
            boolean commandExecutionEnabled,
            int commandTimeoutSeconds,
            int fileOperationTimeoutSeconds,
            int readFileMaxLines,
            int commandOutputMaxChars,
            int searchMaxResults,
            Boolean enableSubAgentBackground) {
        this(maxTokens, systemPrompt, toolSystemEnabled, commandExecutionEnabled,
            commandTimeoutSeconds, fileOperationTimeoutSeconds, readFileMaxLines,
            commandOutputMaxChars, searchMaxResults, enableSubAgentBackground, false, false);
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
        if (coordinatorMode == null) {
            coordinatorMode = false;
        }
        if (forkTeammate == null) {
            forkTeammate = false;
        }
    }

    public Options withSystemPrompt(String newSystemPrompt) {
        return new Options(maxTokens, newSystemPrompt, toolSystemEnabled,
            commandExecutionEnabled, commandTimeoutSeconds,
            fileOperationTimeoutSeconds, readFileMaxLines,
            commandOutputMaxChars, searchMaxResults, enableSubAgentBackground,
            coordinatorMode, forkTeammate);
    }

    public Options withCoordinatorMode(boolean enabled) {
        return new Options(maxTokens, systemPrompt, toolSystemEnabled,
            commandExecutionEnabled, commandTimeoutSeconds,
            fileOperationTimeoutSeconds, readFileMaxLines,
            commandOutputMaxChars, searchMaxResults, enableSubAgentBackground,
            enabled, forkTeammate);
    }
}
