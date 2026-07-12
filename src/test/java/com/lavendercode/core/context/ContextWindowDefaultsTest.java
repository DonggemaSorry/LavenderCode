package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContextWindowDefaultsTest {
    @Test
    void anthropicDefaultIs200k() {
        assertThat(ContextWindowDefaults.resolve("anthropic", null)).isEqualTo(200_000);
    }

    @Test
    void openaiDefaultIs128k() {
        assertThat(ContextWindowDefaults.resolve("openai", null)).isEqualTo(128_000);
    }

    @Test
    void configuredOverridesDefault() {
        assertThat(ContextWindowDefaults.resolve("anthropic", 100_000)).isEqualTo(100_000);
    }

    @Test
    void autoTriggerThreshold() {
        int window = 100_000;
        int threshold = ContextWindowDefaults.autoCompactThreshold(window);
        assertThat(threshold).isEqualTo(67_000);
    }
}
