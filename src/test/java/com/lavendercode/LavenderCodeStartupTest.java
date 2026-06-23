package com.lavendercode;

import com.lavendercode.core.config.*;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LavenderCodeStartupTest {

    @Test
    void shouldCreateSessionWithProviderFromConfig(@TempDir Path tempDir) throws Exception {
        // Write a config file
        String yaml = """
            providers:
              - protocol: anthropic
                model: claude-sonnet-4-20250514
                base_url: https://api.anthropic.com
                api_key: sk-ant-test
            options:
              max_tokens: 4096
              system_prompt: "You are a helpful assistant."
            """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        // Load config
        LlmConfig config = ConfigLoader.load(configFile);
        assertThat(config.providers().get(0).protocol()).isEqualTo("anthropic");
        assertThat(config.providers().get(0).model()).isEqualTo("claude-sonnet-4-20250514");

        // Get provider from registry
        LlmProvider provider = ProviderRegistry.get(config.providers().get(0).protocol());
        assertThat(provider).isNotNull();
        assertThat(provider.protocol()).isEqualTo("anthropic");

        // Create session
        SessionManager session = new InMemorySessionManager();
        assertThat(session.getMessageCount()).isZero();
    }

    @Test
    void shouldLoadAnthropicExampleConfig() throws Exception {
        Path configFile = Path.of("config.yaml.example");
        if (!Files.exists(configFile)) {
            return; // skip if example doesn't exist
        }

        LlmConfig config = ConfigLoader.load(configFile);
        assertThat(config.providers().get(0).protocol()).isEqualTo("anthropic");
    }
}
