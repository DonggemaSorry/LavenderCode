package com.lavendercode.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadValidAnthropicConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-anthropic.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.provider().protocol()).isEqualTo("anthropic");
        assertThat(config.provider().model()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(config.provider().baseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(config.provider().apiKey()).isEqualTo("sk-ant-test-key");
        assertThat(config.options().maxTokens()).isEqualTo(4096);
        assertThat(config.options().systemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(config.options().thinking().enabled()).isTrue();
        assertThat(config.options().thinking().budgetTokens()).isEqualTo(4000);
    }

    @Test
    void shouldLoadValidOpenAIConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-openai.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.provider().protocol()).isEqualTo("openai");
        assertThat(config.provider().model()).isEqualTo("gpt-4o");
        assertThat(config.options().maxTokens()).isEqualTo(2048);
    }

    @Test
    void shouldThrowWhenMissingApiKey() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-missing-api-key.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("apiKey");
    }

    @Test
    void shouldThrowWhenInvalidYaml() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-invalid-yaml.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("YAML");
    }

    @Test
    void shouldThrowWhenFileNotFound() {
        Path nonExistent = tempDir.resolve("nonexistent.yaml");

        assertThatThrownBy(() -> ConfigLoader.load(nonExistent))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldUseDefaultOptionsWhenNotProvided() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-openai.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.options().systemPrompt()).isEmpty();
        assertThat(config.options().thinking().enabled()).isFalse();
        assertThat(config.options().thinking().budgetTokens()).isEqualTo(1024);
    }
}
