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
    ThinkingConfig thinking,

    @JsonProperty("context_window")
    Integer contextWindow
) {
    public ProviderConfig {
        if (thinking == null) {
            thinking = new ThinkingConfig();
        }
    }

    /** Backward-compatible factory without context_window. */
    public static ProviderConfig of(String name, String protocol, String model,
                                    String baseUrl, String apiKey, ThinkingConfig thinking) {
        return new ProviderConfig(name, protocol, model, baseUrl, apiKey, thinking, null);
    }
}
