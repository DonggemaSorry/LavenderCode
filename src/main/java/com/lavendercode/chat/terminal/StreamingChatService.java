package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class StreamingChatService implements ChatService {

    private final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lavender-io");
        t.setDaemon(true);
        return t;
    });

    @Override
    public RequestContext submit(LlmProvider provider,
                                 List<Message> history,
                                 LlmConfig config,
                                 Consumer<DeltaEvent> onDelta) {
        StreamEventIterator iterator = provider.streamChat(history, config);
        RequestContext ctx = new RequestContext(null, iterator);

        ioPool.submit(() -> {
            try {
                while (iterator.hasNext() && !ctx.isCancelled()) {
                    StreamEvent se = iterator.next();
                    if (ctx.isCancelled()) break;
                    DeltaEvent de = toDeltaEvent(se);
                    if (de != null) {
                        onDelta.accept(de);
                    }
                }
                if (!ctx.isCancelled()) {
                    onDelta.accept(new DeltaEvent.Complete());
                }
            } catch (Exception e) {
                if (!ctx.isCancelled()) {
                    onDelta.accept(new DeltaEvent.Error(e.getMessage(), 0));
                }
            } finally {
                iterator.close();
            }
        });

        return ctx;
    }

    @Override
    public void cancel(RequestContext ctx) {
        ctx.cancel();
    }

    private DeltaEvent toDeltaEvent(StreamEvent se) {
        return switch (se) {
            case StreamEvent.ContentDelta cd  -> new DeltaEvent.Content(cd.text());
            case StreamEvent.ThinkingDelta td -> new DeltaEvent.Thinking(td.text());
            case StreamEvent.StreamComplete sc -> null;
            case StreamEvent.StreamError err  -> new DeltaEvent.Error(err.message(), err.statusCode());
        };
    }
}
