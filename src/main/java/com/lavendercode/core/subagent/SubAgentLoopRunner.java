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
            // 队员未读邮箱 → 注入会话
            var incoming = com.lavendercode.core.team.IncomingMailHook.poll();
            if (incoming.isPresent()) {
                session.addUserMessage(incoming.get());
            }
            if (com.lavendercode.core.team.PlanApprovalState.consumeApproved()) {
                // 获批后允许后续使用写工具：本轮起改用非 PLAN 工具集由外层 def 控制；
                // 此处附加提示
                session.addUserMessage("<system-reminder>Plan 已获 Lead 批准，继续执行计划。</system-reminder>");
            } else {
                String feedback = com.lavendercode.core.team.PlanApprovalState.consumeFeedback();
                if (feedback != null) {
                    session.addUserMessage("Plan 被驳回，请根据反馈修改后重新提交: " + feedback);
                }
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
