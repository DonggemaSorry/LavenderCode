package com.lavendercode.core.provider;

import com.lavendercode.core.openai.OpenAIProvider;

class OpenAIProviderContractTest extends LlmProviderContractTest {

    @Override
    LlmProvider provider() {
        return new OpenAIProvider();
    }

    @Override
    String validModel() {
        return "gpt-4o";
    }

    @Override
    String sseResponse() {
        return "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
               "data: [DONE]\n\n";
    }
}
