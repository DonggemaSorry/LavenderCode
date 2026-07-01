package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ReActLoop {
    private final LlmProvider provider;
    private final SessionManager sessionManager;
    private final BatchingToolExecutor batchExecutor;
    private final TokenAccumulator tokenAccumulator;
    private final int maxIterations;
    private final int maxUnknownRounds;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    private List<ToolDefinition> toolDefs = List.of();
    private LlmConfig config = null;

    public ReActLoop(LlmProvider provider, SessionManager sessionManager,
                     BatchingToolExecutor batchExecutor, TokenAccumulator tokenAccumulator,
                     int maxIterations, int maxUnknownRounds) {
        this.provider = provider;
        this.sessionManager = sessionManager;
        this.batchExecutor = batchExecutor;
        this.tokenAccumulator = tokenAccumulator;
        this.maxIterations = maxIterations;
        this.maxUnknownRounds = maxUnknownRounds;
    }

    public void setConfig(LlmConfig config, List<ToolDefinition> toolDefs) {
        this.config = config;
        this.toolDefs = toolDefs != null ? toolDefs : List.of();
    }

    public void cancel() { cancelFlag.set(true); }

    public void run(String userMessage, Consumer<AgentEvent> sink) {
        sessionManager.addUserMessage(userMessage);
        cancelFlag.set(false);
        int iteration = 0;
        int unknownStreak = 0;

        while (true) {
            iteration++;
            sink.accept(new AgentEvent.RoundStart(iteration));

            // 1. Stream collect
            StreamEventIterator iter = provider.streamChat(sessionManager.getHistory(), config, toolDefs);
            RoundCollector collector = new RoundCollector(sink);
            RoundResult result = collector.consume(iter, cancelFlag);

            // 2. Stream error
            if (result.hasError()) {
                sink.accept(new AgentEvent.Error(result.error()));
                return; // AC5
            }

            // 3. Cancel during streaming — discard, don't write
            if (cancelFlag.get()) {
                sink.accept(new AgentEvent.Stopped(AgentEvent.StopReason.USER_CANCELLED, "用户中断"));
                return; // AC10
            }

            // 4. Token usage
            tokenAccumulator.add(result.inputTokens(), result.outputTokens());
            sink.accept(new AgentEvent.Usage(tokenAccumulator.getTotalInput(), tokenAccumulator.getTotalOutput()));

            // 5. Natural completion
            if (result.noTools()) {
                sessionManager.addAssistantMessage(result.fullText());
                sink.accept(new AgentEvent.Complete());
                return; // AC2
            }

            // 6. Unknown tools check
            if (allUnknown(result.toolCalls())) {
                unknownStreak++;
            } else {
                unknownStreak = 0;
            }

            // 7. Execute tools
            List<ToolResult> toolResults = batchExecutor.execute(result.toolCalls(), sink, cancelFlag);
            for (int i = 0; i < result.toolCalls().size(); i++) {
                ToolCall tc = result.toolCalls().get(i);
                sink.accept(new AgentEvent.ToolResultReady(tc.id(), toolResults.get(i)));
            }

            // 8. Atomic write to history
            sessionManager.addToolMessages(result.toolCalls(), toolResults);

            // 9. Cancel after execution
            if (cancelFlag.get()) {
                sink.accept(new AgentEvent.Stopped(AgentEvent.StopReason.USER_CANCELLED, "用户中断"));
                return; // AC10
            }

            // 10. Max iterations (checked before unknown tools — system limit takes priority)
            if (iteration >= maxIterations) {
                sink.accept(new AgentEvent.Stopped(AgentEvent.StopReason.MAX_ITERATIONS, "已达迭代上限"));
                return; // AC3
            }

            // 11. Unknown tools stop
            if (unknownStreak >= maxUnknownRounds) {
                sink.accept(new AgentEvent.Stopped(AgentEvent.StopReason.UNKNOWN_TOOLS, "连续请求未知工具"));
                return; // AC4
            }

            sink.accept(new AgentEvent.RoundEnd(iteration));
        }
    }

    private boolean allUnknown(List<ToolCall> calls) {
        return calls.stream().allMatch(tc -> ToolRegistry.get(tc.name()) == null);
    }
}
