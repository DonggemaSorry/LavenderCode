package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProviderConfig(
    @JsonProperty("protocol")
    @NotNull
    String protocol,

    @JsonProperty("model")
    @NotNull
    String model,

    @JsonProperty("base_url")
    @NotNull
    String baseUrl,

    @JsonProperty("api_key")
    @NotNull
    String apiKey
) {}
