package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;
import java.util.List;
import java.util.function.Consumer;

public interface ChatService {
    RequestContext submit(LlmProvider provider,
                          List<Message> history,
                          LlmConfig config,
                          Consumer<DeltaEvent> onDelta);
    void cancel(RequestContext ctx);
}
