package com.lavendercode.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigContextWindowTest {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void parsesOptionalContextWindow() throws Exception {
        String yamlText = """
            name: test
            protocol: anthropic
            model: claude-sonnet-4
            api_key: key
            context_window: 100000
            """;
        ProviderConfig cfg = yaml.readValue(yamlText, ProviderConfig.class);
        assertThat(cfg.contextWindow()).isEqualTo(100_000);
    }

    @Test
    void absentContextWindowIsNull() {
        ProviderConfig cfg = new ProviderConfig(null, "openai", "gpt-4", null, "key", null, null);
        assertThat(cfg.contextWindow()).isNull();
    }
}
