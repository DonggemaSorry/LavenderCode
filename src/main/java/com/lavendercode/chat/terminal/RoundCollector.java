package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolCallAccumulator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class RoundCollector {
    private final Consumer<AgentEvent> sink;
    private final StringBuilder fullText = new StringBuilder();
    private final ToolCallAccumulator accumulator = new ToolCallAccumulator();
    private final List<ToolCall> completedCalls = new ArrayList<>();
    private int inputTokens = 0, outputTokens = 0;
    private int cacheCreationTokens = 0, cacheReadTokens = 0;
    private String error = null;

    public RoundCollector(Consumer<AgentEvent> sink) {
        this.sink = sink;
    }

    public RoundResult consume(StreamEventIterator iter, AtomicBoolean cancelFlag) {
        while (iter.hasNext() && !cancelFlag.get()) {
            StreamEvent se = iter.next();
            switch (se) {
                case StreamEvent.ContentDelta cd -> {
                    fullText.append(cd.text());
                    sink.accept(new AgentEvent.Content(cd.text()));
                }
                case StreamEvent.ToolCallStart tcs -> {
                    accumulator.start(tcs.toolCallId(), tcs.toolName());
                    sink.accept(new AgentEvent.ToolCallStart(tcs.toolCallId(), tcs.toolName()));
                }
                case StreamEvent.ToolCallDelta tcd -> accumulator.append(tcd.toolCallId(), tcd.jsonFragment());
                case StreamEvent.ToolCallEnd tce -> {
                    ToolCall call = accumulator.complete(tce.toolCallId());
                    if (call == null) {
                        call = new ToolCall(tce.toolCallId(), tce.toolName(), tce.parameters());
                    }
                    completedCalls.add(call);
                    sink.accept(new AgentEvent.ToolCallEnd(call));
                }
                case StreamEvent.Usage u -> {
                    inputTokens = u.inputTokens();
                    outputTokens = u.outputTokens();
                    cacheCreationTokens = u.cacheCreationTokens();
                    cacheReadTokens = u.cacheReadTokens();
                }
                case StreamEvent.StreamError err -> {
                    error = err.message();
                    return finish(iter);
                }
                case StreamEvent.StreamComplete sc -> {
                    return finish(null);
                }
                case StreamEvent.ThinkingDelta td -> { /* discard */ }
            }
        }
        if (cancelFlag.get()) {
            iter.close();
        }
        return finish(null);
    }

    private RoundResult finish(StreamEventIterator iter) {
        if (iter != null) {
            iter.close();
        }
        return new RoundResult(fullText.toString(), List.copyOf(completedCalls),
                               inputTokens, outputTokens,
                               cacheCreationTokens, cacheReadTokens, error);
    }
}
