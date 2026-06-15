package com.lavendercode.core.provider;

import com.lavendercode.core.anthropic.AnthropicProvider;

class AnthropicProviderContractTest extends LlmProviderContractTest {

    @Override
    LlmProvider provider() {
        return new AnthropicProvider();
    }

    @Override
    String validModel() {
        return "claude-sonnet-4-20250514";
    }

    @Override
    String sseResponse() {
        return "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n" +
               "data: {\"type\":\"message_stop\"}\n\n";
    }
}
