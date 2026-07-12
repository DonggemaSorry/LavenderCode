package com.lavendercode.core.context;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CompactionService {
    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final LlmConfig config;
    private final Layer1Offloader layer1;
    private final TokenEstimator tokenEstimator;
    private final FileReadTracker fileReadTracker;
    private final AutoCompactCircuitBreaker circuitBreaker;
    private final int contextWindow;
    private final RecentTailSelector tailSelector;
    private final RecoverySegmentBuilder recoveryBuilder;
    private Consumer<ContextEvent> eventSink = e -> {};

    public CompactionService(SessionManager sessionManager,
                             LlmProvider provider,
                             LlmConfig config,
                             Layer1Offloader layer1,
                             TokenEstimator tokenEstimator,
                             FileReadTracker fileReadTracker,
                             AutoCompactCircuitBreaker circuitBreaker,
                             int contextWindow) {
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.config = config;
        this.layer1 = layer1;
        this.tokenEstimator = tokenEstimator;
        this.fileReadTracker = fileReadTracker;
        this.circuitBreaker = circuitBreaker;
        this.contextWindow = contextWindow;
        this.tailSelector = new RecentTailSelector(tokenEstimator);
        this.recoveryBuilder = new RecoverySegmentBuilder(fileReadTracker);
    }

    public void setEventSink(Consumer<ContextEvent> sink) {
        this.eventSink = sink != null ? sink : e -> {};
    }

    public CompactResult compact(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
        int tokensBefore = tokenEstimator.estimateMessages(sessionManager.getHistory());

        if (trigger == CompactTrigger.EMERGENCY) {
            layer1.offloadAndSnip();
        }
        if (trigger == CompactTrigger.AUTO && circuitBreaker.isTripped()) {
            return CompactResult.fail(tokensBefore, "Auto compact circuit breaker is open");
        }

        if (trigger == CompactTrigger.AUTO || trigger == CompactTrigger.EMERGENCY) {
            eventSink.accept(new ContextEvent.Compacting(
                trigger == CompactTrigger.EMERGENCY
                    ? "上下文撞墙,自动压缩中..."
                    : "正在压缩上下文..."));
        }

        try {
            List<Message> history = sessionManager.getHistory();
            String summaryText = requestSummary(history, trigger);
            String parsed = SummaryResponseParser.extractSummary(summaryText);

            List<Message> tail = tailSelector.select(history);
            List<Message> newHistory = new ArrayList<>();
            newHistory.add(new Message(Role.USER, "## Conversation Summary\n" + parsed));
            newHistory.addAll(recoveryBuilder.build(toolDefs));
            newHistory.addAll(tail);
            sessionManager.replaceHistory(newHistory);

            tokenEstimator.resetAnchor();
            tokenEstimator.syncCharCount(newHistory);
            int tokensAfter = tokenEstimator.estimateMessages(newHistory);

            if (trigger == CompactTrigger.AUTO) {
                circuitBreaker.recordSuccess();
            }
            if (trigger != CompactTrigger.MANUAL) {
                eventSink.accept(new ContextEvent.Compacted(tokensBefore, tokensAfter));
            }
            return CompactResult.ok(tokensBefore, tokensAfter);
        } catch (CompactionException e) {
            if (trigger == CompactTrigger.AUTO) {
                circuitBreaker.recordFailure();
            }
            if (trigger != CompactTrigger.MANUAL) {
                eventSink.accept(new ContextEvent.CompactFailed(e.getMessage()));
            }
            return CompactResult.fail(tokensBefore, e.getMessage());
        }
    }

    private String requestSummary(List<Message> history, CompactTrigger trigger) {
        List<List<Message>> groups = MessageGroupDropper.group(history);
        int ptlAttempts = 0;

        while (true) {
            List<Message> input = buildSummaryInput(MessageGroupDropper.flatten(groups));
            SummaryStreamResult result = streamSummary(input);

            if (result.error == null) {
                return result.text;
            }
            if (!PromptTooLongDetector.isPromptTooLong(result.error, result.statusCode)) {
                throw new CompactionException(result.error);
            }

            ptlAttempts++;
            if (ptlAttempts <= ContextConstants.PTL_DIRECT_RETRY_LIMIT) {
                groups = MessageGroupDropper.dropOldest(groups, 1);
            } else if (groups.isEmpty()) {
                throw new CompactionException("Summary request failed: " + result.error);
            } else {
                groups = MessageGroupDropper.dropOldest(groups, MessageGroupDropper.ratioDropCount(groups.size()));
            }
            if (groups.isEmpty()) {
                throw new CompactionException("Summary request failed after dropping all message groups");
            }
        }
    }

    private List<Message> buildSummaryInput(List<Message> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(Role.USER, SummaryPromptBuilder.compactionInstruction()));
        messages.addAll(history);
        return messages;
    }

    private SummaryStreamResult streamSummary(List<Message> messages) {
        StringBuilder text = new StringBuilder();
        StreamEventIterator iter = provider.streamChat(messages, config, List.of());
        AtomicBoolean cancel = new AtomicBoolean(false);
        try {
            while (iter.hasNext()) {
                StreamEvent se = iter.next();
                switch (se) {
                    case StreamEvent.ContentDelta cd -> text.append(cd.text());
                    case StreamEvent.StreamError err -> {
                        return new SummaryStreamResult(text.toString(), err.message(), err.statusCode());
                    }
                    case StreamEvent.StreamComplete sc -> {
                        return new SummaryStreamResult(text.toString(), null, 0);
                    }
                    default -> { }
                }
            }
        } finally {
            iter.close();
        }
        return new SummaryStreamResult(text.toString(), null, 0);
    }

    private record SummaryStreamResult(String text, String error, int statusCode) {}
}
