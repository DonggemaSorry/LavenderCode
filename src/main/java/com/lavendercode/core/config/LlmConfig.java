package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record LlmConfig(
    @JsonProperty("provider")
    @NotNull
    @Valid
    ProviderConfig provider,

    @JsonProperty("options")
    Options options
) {
    public LlmConfig {
        if (options == null) {
            options = new Options();
        }
    }
}
