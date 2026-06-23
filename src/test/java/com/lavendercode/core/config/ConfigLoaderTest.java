package com.lavendercode.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadSingleProviderConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-anthropic.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        List<ProviderConfig> providers = config.providers();
        assertThat(providers).hasSize(1);
        assertThat(providers.get(0).protocol()).isEqualTo("anthropic");
        assertThat(providers.get(0).model()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(providers.get(0).baseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(providers.get(0).apiKey()).isEqualTo("sk-ant-test-key");
        assertThat(providers.get(0).name()).isNull();
        assertThat(providers.get(0).thinking().enabled()).isTrue();
        assertThat(providers.get(0).thinking().budgetTokens()).isEqualTo(4000);
        assertThat(config.options().maxTokens()).isEqualTo(4096);
        assertThat(config.options().systemPrompt()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void shouldLoadMultiProviderConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-multi-provider.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers()).hasSize(2);
        assertThat(config.providers().get(0).name()).isEqualTo("DeepSeek");
        assertThat(config.providers().get(0).protocol()).isEqualTo("openai");
        assertThat(config.providers().get(1).name()).isEqualTo("Claude");
        assertThat(config.providers().get(1).protocol()).isEqualTo("anthropic");
    }

    @Test
    void shouldAcceptNullBaseUrl() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).baseUrl()).isNull();
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
    void shouldThrowWhenEmptyProviders() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-empty-providers.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("empty");
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
        assertThat(config.options().maxTokens()).isEqualTo(2048);
    }

    @Test
    void shouldLoadConfigWithOnlyRequiredFields() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).protocol()).isEqualTo("openai");
        assertThat(config.providers().get(0).apiKey()).isEqualTo("sk-test");
        assertThat(config.options().maxTokens()).isEqualTo(4096);
        assertThat(config.options().systemPrompt()).isEmpty();
    }

    @Test
    void shouldHandleSystemPromptWithSpecialCharacters() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: anthropic
                model: claude-sonnet-4-20250514
                api_key: sk-ant-test
            options:
              system_prompt: "You are helpful. Use 你好 for greetings."
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.options().systemPrompt())
            .isEqualTo("You are helpful. Use 你好 for greetings.");
    }

    @Test
    void shouldUseDefaultThinkingWhenNotProvided() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).thinking().enabled()).isFalse();
        assertThat(config.providers().get(0).thinking().budgetTokens()).isEqualTo(1024);
    }
}
