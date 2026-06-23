package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;

public record LlmConfig(
    @JsonProperty("providers")
    @NotNull
    @Valid
    List<ProviderConfig> providers,

    @JsonProperty("options")
    Options options
) {
    public LlmConfig {
        if (options == null) {
            options = new Options();
        }
    }

    /** Returns an unmodifiable view of the providers list. */
    public List<ProviderConfig> providers() {
        return Collections.unmodifiableList(providers);
    }
}
