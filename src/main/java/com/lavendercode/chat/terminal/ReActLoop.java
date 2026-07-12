package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.prompt.ReminderInjector;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ReActLoop {
    private final LlmProvider provider;
    private final SessionManager sessionManager;
    private final BatchingToolExecutor batchExecutor;
    private final TokenAccumulator tokenAccumulator;
    private final ContextManager contextManager;
    private final int maxIterations;
    private final int maxUnknownRounds;
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    private static final Logger logger = Logger.getLogger(ReActLoop.class.getName());
    private List<ToolDefinition> toolDefs = List.of();
    private LlmConfig config = null;
    private String stablePrompt;
    private String environmentInfo;
    private PermissionModeManager modeManager;

    public ReActLoop(LlmProvider provider, SessionManager sessionManager,
                     BatchingToolExecutor batchExecutor, TokenAccumulator tokenAccumulator,
                     int maxIterations, int maxUnknownRounds, ContextManager contextManager) {
        this.provider = provider;
        this.sessionManager = sessionManager;
        this.batchExecutor = batchExecutor;
        this.tokenAccumulator = tokenAccumulator;
        this.contextManager = contextManager != null ? contextManager : com.lavendercode.core.context.NoOpContextManager.INSTANCE;
        this.maxIterations = maxIterations;
        this.maxUnknownRounds = maxUnknownRounds;
    }

    public void setConfig(LlmConfig config, List<ToolDefinition> toolDefs) {
        this.config = config;
        this.toolDefs = toolDefs != null ? toolDefs : List.of();
    }

    public void setConfig(LlmConfig config, List<ToolDefinition> toolDefs,
                          String stablePrompt, String environmentInfo,
                          PermissionModeManager modeManager) {
        this.config = config;
        this.toolDefs = toolDefs != null ? toolDefs : List.of();
        this.stablePrompt = stablePrompt;
        this.environmentInfo = environmentInfo;
        this.modeManager = modeManager;
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

            // ch05: build PromptContext per round
            PromptContext promptCtx = null;
            if (stablePrompt != null) {
                Optional<String> reminder = ReminderInjector.inject(
                    iteration, modeManager != null && modeManager.isPlanMode());
                promptCtx = new PromptContext(stablePrompt, environmentInfo,
                    reminder.map(List::of).orElse(List.of()));
            }

            contextManager.manageContext(CompactTrigger.AUTO, toolDefs);

            // 1. Stream collect
            boolean emergencyRetried = false;
            RoundResult result;
            while (true) {
                StreamEventIterator iter = (promptCtx != null)
                    ? provider.streamChat(sessionManager.getHistory(), config, toolDefs, promptCtx)
                    : provider.streamChat(sessionManager.getHistory(), config, toolDefs);
                RoundCollector collector = new RoundCollector(sink);
                result = collector.consume(iter, cancelFlag);

                if (result.hasError() && contextManager.isPromptTooLong(result.error())) {
                    if (!emergencyRetried) {
                        contextManager.runCompaction(CompactTrigger.EMERGENCY, toolDefs);
                        contextManager.resetAnchor();
                        emergencyRetried = true;
                        continue;
                    }
                }
                break;
            }

            // ch05: cache hit info to debug log only
            if (result.cacheReadTokens() > 0 || result.cacheCreationTokens() > 0) {
                logger.fine("Round " + iteration + " cache: creation="
                    + result.cacheCreationTokens() + " read=" + result.cacheReadTokens());
            }

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
            contextManager.onUsage(result);
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

            contextManager.recordFileReads(result.toolCalls(), toolResults);

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
