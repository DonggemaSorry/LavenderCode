package com.lavendercode.core.context;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.RoundResult;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import java.util.List;
import java.util.function.Consumer;

public final class DefaultContextManager implements ContextManager {
    private final SessionManager sessionManager;
    private final Layer1Offloader layer1;
    private final CompactionService compactionService;
    private final TokenEstimator tokenEstimator;
    private final FileReadTracker fileReadTracker;
    private final int contextWindow;
    private final AutoCompactCircuitBreaker circuitBreaker;

    public DefaultContextManager(SessionManager sessionManager,
                                 Layer1Offloader layer1,
                                 CompactionService compactionService,
                                 TokenEstimator tokenEstimator,
                                 FileReadTracker fileReadTracker,
                                 int contextWindow,
                                 AutoCompactCircuitBreaker circuitBreaker) {
        this.sessionManager = sessionManager;
        this.layer1 = layer1;
        this.compactionService = compactionService;
        this.tokenEstimator = tokenEstimator;
        this.fileReadTracker = fileReadTracker;
        this.contextWindow = contextWindow;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ManageOutcome manageContext(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
        if (trigger != CompactTrigger.AUTO) {
            return ManageOutcome.UNCHANGED;
        }
        layer1.offloadAndSnip();
        int estimate = tokenEstimator.estimateMessages(sessionManager.getHistory());
        int threshold = ContextWindowDefaults.autoCompactThreshold(contextWindow);
        if (estimate >= threshold && !circuitBreaker.isTripped()) {
            CompactResult result = compactionService.compact(CompactTrigger.AUTO, toolDefs);
            if (result.success()) {
                return ManageOutcome.COMPACTED;
            }
            tokenEstimator.syncCharCount(sessionManager.getHistory());
            return ManageOutcome.SKIPPED;
        }
        tokenEstimator.syncCharCount(sessionManager.getHistory());
        return ManageOutcome.LAYER1_ONLY;
    }

    @Override
    public CompactResult runCompaction(CompactTrigger trigger, List<ToolDefinition> toolDefs) {
        return compactionService.compact(trigger, toolDefs);
    }

    @Override
    public void onUsage(RoundResult result) {
        if (result.hasError()) return;
        tokenEstimator.replaceAnchor(
            result.inputTokens(), result.cacheReadTokens(),
            result.cacheCreationTokens(), result.outputTokens());
        tokenEstimator.syncCharCount(sessionManager.getHistory());
    }

    @Override
    public void recordFileReads(List<ToolCall> calls, List<ToolResult> results) {
        fileReadTracker.record(calls, results);
    }

    @Override
    public void resetAnchor() {
        tokenEstimator.resetAnchor();
    }

    @Override
    public boolean isPromptTooLong(String errorMessage) {
        return PromptTooLongDetector.isPromptTooLong(errorMessage, 0);
    }

    @Override
    public void setEventSink(Consumer<ContextEvent> sink) {
        compactionService.setEventSink(sink);
    }
}
