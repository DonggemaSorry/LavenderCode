package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProviderConfig(
    @JsonProperty("name")
    String name,

    @JsonProperty("protocol")
    @NotNull
    String protocol,

    @JsonProperty("model")
    @NotNull
    String model,

    @JsonProperty("base_url")
    String baseUrl,

    @JsonProperty("api_key")
    @NotNull
    String apiKey,

    @JsonProperty("thinking")
    ThinkingConfig thinking
) {
    public ProviderConfig {
        if (thinking == null) {
            thinking = new ThinkingConfig();
        }
    }
}
