package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.sse.SseStreamEventIterator;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolCallAccumulator;
import com.lavendercode.core.tool.ToolDefinition;

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
    private final ToolCallAccumulator toolCallAccumulator = new ToolCallAccumulator();

    @Override
    public RequestContext submit(LlmProvider provider,
                                 List<Message> history,
                                 LlmConfig config,
                                 Consumer<DeltaEvent> onDelta) {
        toolCallAccumulator.clear();
        RequestContext ctx = new RequestContext(null, null);

        ioPool.submit(() -> {
            StreamEventIterator iterator = null;
            try {
                iterator = provider.streamChat(history, config);
                ctx.bind(iterator, iterator instanceof SseStreamEventIterator sse ? sse.call() : null);

                while (iterator.hasNext() && !ctx.isCancelled()) {
                    StreamEvent se = iterator.next();
                    if (ctx.isCancelled()) {
                        break;
                    }
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
                if (iterator != null) {
                    iterator.close();
                }
            }
        });

        return ctx;
    }

    @Override
    public RequestContext submit(LlmProvider provider,
                                 List<Message> history,
                                 LlmConfig config,
                                 List<ToolDefinition> toolDefs,
                                 Consumer<DeltaEvent> onDelta) {
        toolCallAccumulator.clear();
        RequestContext ctx = new RequestContext(null, null);

        ioPool.submit(() -> {
            StreamEventIterator iterator = null;
            try {
                iterator = provider.streamChat(history, config, toolDefs);
                ctx.bind(iterator, iterator instanceof SseStreamEventIterator sse ? sse.call() : null);

                while (iterator.hasNext() && !ctx.isCancelled()) {
                    StreamEvent se = iterator.next();
                    if (ctx.isCancelled()) {
                        break;
                    }
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
                if (iterator != null) {
                    iterator.close();
                }
            }
        });

        return ctx;
    }

    @Override
    public void cancel(RequestContext ctx) {
        ctx.cancel();
    }

    @Override
    public void shutdown() {
        ioPool.shutdownNow();
        try {
            ioPool.awaitTermination(50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private DeltaEvent toDeltaEvent(StreamEvent se) {
        return switch (se) {
            case StreamEvent.ContentDelta cd  -> new DeltaEvent.Content(cd.text());
            case StreamEvent.ThinkingDelta td -> null;
            case StreamEvent.ToolCallStart tcs -> {
                toolCallAccumulator.start(tcs.toolCallId(), tcs.toolName());
                yield new DeltaEvent.ToolCallStart(tcs.toolCallId(), tcs.toolName());
            }
            case StreamEvent.ToolCallDelta tcd -> {
                toolCallAccumulator.append(tcd.toolCallId(), tcd.jsonFragment());
                yield null;
            }
            case StreamEvent.ToolCallEnd tce -> {
                ToolCall call = toolCallAccumulator.complete(tce.toolCallId());
                yield new DeltaEvent.ToolCallEnd(call);
            }
            case StreamEvent.StreamComplete sc -> null;
            case StreamEvent.StreamError err  -> new DeltaEvent.Error(err.message(), err.statusCode());
            case StreamEvent.Usage u -> new DeltaEvent.Usage(u.inputTokens(), u.outputTokens());
        };
    }
}
