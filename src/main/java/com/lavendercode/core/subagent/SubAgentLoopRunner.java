package com.lavendercode.core.subagent;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SubAgentLoopRunner {

    @FunctionalInterface
    public interface ToolBatchExecutor {
        List<ToolResult> execute(List<ToolCall> calls, AtomicBoolean cancelFlag);
    }

    private final LlmProvider provider;
    private final LlmConfig config;
    private final ToolBatchExecutor toolExecutor;
    private final int maxTurns;

    public SubAgentLoopRunner(LlmProvider provider, LlmConfig config,
                              ToolBatchExecutor toolExecutor, int maxTurns) {
        this.provider = provider;
        this.config = config;
        this.toolExecutor = toolExecutor;
        this.maxTurns = maxTurns;
    }

    public String runToCompletion(SessionManager session, List<ToolDefinition> toolDefs,
                                  String task, AtomicBoolean cancelFlag) {
        session.addUserMessage(task);
        String lastText = "";
        for (int iteration = 1; iteration <= maxTurns; iteration++) {
            if (cancelFlag.get()) {
                break;
            }
            StreamEventIterator iter = provider.streamChat(session.getHistory(), config, toolDefs);
            StreamRoundCollector collector = new StreamRoundCollector();
            StreamRoundCollector.SubAgentRoundResult result = collector.consume(iter, cancelFlag);

            if (result.hasError()) {
                return "Error: " + result.error();
            }
            lastText = result.fullText();

            if (result.noTools()) {
                if (!lastText.isBlank()) {
                    session.addAssistantMessage(lastText);
                }
                return lastText;
            }

            List<ToolResult> toolResults = toolExecutor.execute(result.toolCalls(), cancelFlag);
            session.addToolMessages(result.toolCalls(), toolResults);

            if (cancelFlag.get()) {
                break;
            }
        }
        if (!lastText.isBlank()) {
            session.addAssistantMessage(lastText);
            return lastText + "\n[达到最大轮数]";
        }
        return "[达到最大轮数]";
    }
}
