package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ThinkingConfig(
    @JsonProperty("enabled")
    boolean enabled,

    @JsonProperty("budget_tokens")
    int budgetTokens
) {
    public ThinkingConfig() {
        this(false, 1024);
    }
}
