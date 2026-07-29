package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolCallAccumulator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class StreamRoundCollector {
    private static final int MAX_TEXT_CHARS = 8_000_000;
    private final StringBuilder fullText = new StringBuilder();
    private final ToolCallAccumulator accumulator = new ToolCallAccumulator();
    private final List<ToolCall> completedCalls = new ArrayList<>();
    private String error;

    SubAgentRoundResult consume(StreamEventIterator iter, AtomicBoolean cancelFlag) {
        // finally 兜底关闭：任何退出路径都必须释放 HTTP 连接（close 幂等）
        try {
            while (iter.hasNext() && !cancelFlag.get()) {
                StreamEvent se = iter.next();
                switch (se) {
                    case StreamEvent.ContentDelta cd -> {
                        fullText.append(cd.text());
                        if (fullText.length() > MAX_TEXT_CHARS) {
                            error = "stream text exceeded " + MAX_TEXT_CHARS + " chars";
                            return finish();
                        }
                    }
                    case StreamEvent.ToolCallStart tcs -> accumulator.start(tcs.toolCallId(), tcs.toolName());
                    case StreamEvent.ToolCallDelta tcd -> accumulator.append(tcd.toolCallId(), tcd.jsonFragment());
                    case StreamEvent.ToolCallEnd tce -> {
                        ToolCall call = accumulator.complete(tce.toolCallId());
                        if (call == null) {
                            call = new ToolCall(tce.toolCallId(), tce.toolName(), tce.parameters());
                        }
                        completedCalls.add(call);
                    }
                    case StreamEvent.StreamError err -> {
                        error = err.message();
                        return finish();
                    }
                    case StreamEvent.StreamComplete sc -> {
                        return finish();
                    }
                    case StreamEvent.Usage u -> { /* ignore */ }
                    case StreamEvent.ThinkingDelta td -> { /* ignore */ }
                }
            }
            return finish();
        } finally {
            iter.close();
        }
    }

    private SubAgentRoundResult finish() {
        return new SubAgentRoundResult(
            fullText.toString(), List.copyOf(completedCalls), error);
    }

    record SubAgentRoundResult(String fullText, List<ToolCall> toolCalls, String error) {
        boolean hasError() { return error != null; }
        boolean noTools() { return toolCalls.isEmpty(); }
    }
}
