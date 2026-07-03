package com.lavendercode.core.provider;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.tool.ToolDefinition;

import java.util.List;

public interface LlmProvider extends AutoCloseable {
    String protocol();

    StreamEventIterator streamChat(List<Message> history, LlmConfig config);

    /**
     * Stream chat with tool definitions.
     * Default implementation ignores toolDefs for backward compatibility.
     */
    default StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                           List<ToolDefinition> toolDefs) {
        return streamChat(history, config);
    }

    default StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                           List<ToolDefinition> toolDefs,
                                           PromptContext promptContext) {
        return streamChat(history, config, toolDefs);
    }

    @Override
    default void close() {}
}
