package com.lavendercode.core.provider;

import com.lavendercode.core.config.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRegistryTest {

    @Test
    void shouldDiscoverAnthropicProviderViaServiceLoader() {
        LlmProvider provider = ProviderRegistry.get("anthropic");
        assertThat(provider).isNotNull();
        assertThat(provider.protocol()).isEqualTo("anthropic");
    }

    @Test
    void shouldDiscoverOpenAIProviderViaServiceLoader() {
        LlmProvider provider = ProviderRegistry.get("openai");
        assertThat(provider).isNotNull();
        assertThat(provider.protocol()).isEqualTo("openai");
    }

    @Test
    void shouldThrowForUnknownProtocol() {
        assertThatThrownBy(() -> ProviderRegistry.get("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void shouldAllowManualRegistration() {
        LlmProvider custom = new LlmProvider() {
            @Override public String protocol() { return "custom-test"; }
            @Override public StreamEventIterator streamChat(
                    java.util.List<Message> history, LlmConfig config) { return null; }
        };
        ProviderRegistry.register(custom);

        LlmProvider found = ProviderRegistry.get("custom-test");
        assertThat(found.protocol()).isEqualTo("custom-test");
    }
}
