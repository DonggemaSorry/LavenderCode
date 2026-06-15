package com.lavendercode.core.provider;

import com.lavendercode.core.config.LlmConfig;

import java.util.List;

public interface LlmProvider {
    String protocol();

    StreamEventIterator streamChat(List<Message> history, LlmConfig config);
}
