package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.tool.ToolDefinition;
import java.util.List;
import java.util.function.Consumer;

public interface ChatService {
    RequestContext submit(LlmProvider provider,
                          List<Message> history,
                          LlmConfig config,
                          Consumer<DeltaEvent> onDelta);

    default RequestContext submit(LlmProvider provider,
                                  List<Message> history,
                                  LlmConfig config,
                                  List<ToolDefinition> toolDefs,
                                  Consumer<DeltaEvent> onDelta) {
        return submit(provider, history, config, onDelta);
    }

    void cancel(RequestContext ctx);

    void shutdown();
}
