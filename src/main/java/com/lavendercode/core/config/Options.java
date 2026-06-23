package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Options(
    @JsonProperty("max_tokens")
    int maxTokens,

    @JsonProperty("system_prompt")
    String systemPrompt
) {
    public Options() {
        this(4096, "");
    }

    public Options {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
    }
}
