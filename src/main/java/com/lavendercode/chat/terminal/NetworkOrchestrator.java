package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolRegistry;
import com.lavendercode.core.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkOrchestrator {

    private final AtomicReference<RequestContext> currentRequest = new AtomicReference<>();
    private final ChatService chatService;
    private final DeltaBuffer deltaBuffer;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final BlockingQueue<InputEvent> inputQueue;
    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String providerName;
    private final String modelName;
    private final LlmConfig config;
    private final Options options;
    private final ScheduledExecutorService timerScheduler;
    private volatile ScheduledFuture<?> timerTask;
    private volatile ResponseTimer currentTimer;

    // Tool scheduling state machine
    private enum ToolPhase { IDLE, STREAMING, EXECUTING, REINJECTING, STREAMING_FINAL, DONE }
    private ToolPhase toolPhase = ToolPhase.IDLE;
    private final List<ToolCall> completedToolCalls = new ArrayList<>();
    private String responseContent = "";
    private int pendingToolMsgCount = 0; // tool messages in session to rollback on re-injection failure
    private int reInjectRound = 0;
    private static final int MAX_REINJECT_ROUNDS = 5;

    public NetworkOrchestrator(ChatService chatService, DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler) {
        this.chatService = chatService;
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.providerName = providerName;
        this.modelName = modelName;
        this.config = config;
        this.options = config.options();
        this.timerScheduler = timerScheduler;
    }

    public void run() {
        safePut(new RenderEvent.StatusUpdate(providerName, modelName, "", 0));
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ExecuteCommand cmd -> {
                        if (handleCommand(cmd)) return;
                    }
                    case InputEvent.Shutdown __ -> { handleShutdown(); return; }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSendMessage(InputEvent.SendMessage msg) {
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));
        sessionManager.addUserMessage(msg.text());
        completedToolCalls.clear();
        responseContent = "";
        reInjectRound = 0;
        toolPhase = ToolPhase.STREAMING;

        var ctxRef = new AtomicReference<RequestContext>();
        final ResponseTimer timer = new ResponseTimer();
        timer.start();
        this.currentTimer = timer;
        final ScheduledFuture<?> ft = timerScheduler.scheduleAtFixedRate(() -> {
            safePut(new RenderEvent.StatusUpdate(
                providerName, modelName,
                "Imagining\u2026 (" + timer.elapsedSeconds() + "s)", 0));
        }, 1, 1, TimeUnit.SECONDS);
        this.timerTask = ft;
        try {
            // Use the full agent prompt (identity + capabilities + rules + user prompt)
            var agentPrompt = AgentPromptBuilder.build(options.systemPrompt());
            var promptConfig = new LlmConfig(config.providers(), options.withSystemPrompt(agentPrompt));
            List<ToolDefinition> toolDefs = options.toolSystemEnabled() ? ToolRegistry.export() : List.of();
            if (!toolDefs.isEmpty()) {
                ctxRef.set(chatService.submit(
                    provider, sessionManager.getHistory(), promptConfig, toolDefs,
                    delta -> onDeltaReceived(ctxRef.get(), delta)
                ));
            } else {
                ctxRef.set(chatService.submit(
                    provider, sessionManager.getHistory(), promptConfig,
                    delta -> onDeltaReceived(ctxRef.get(), delta)
                ));
            }
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            if (ft != null) ft.cancel(false);
            currentRequest.set(null);
            deltaBuffer.forceFlush();
            safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
            safePut(new RenderEvent.FinalizeMessage());
            toolPhase = ToolPhase.IDLE;
        }
    }

    private boolean handleCommand(InputEvent.ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
                cancelTimer();
                RequestContext ctx = currentRequest.getAndSet(null);
                if (ctx != null) {
                    chatService.cancel(ctx);
                }
                // Rollback tool messages if re-injection hasn't completed
                rollbackToolMessages();
                // If in EXECUTING phase, terminate without re-injection
                if (toolPhase == ToolPhase.EXECUTING) {
                    toolPhase = ToolPhase.IDLE;
                    completedToolCalls.clear();
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
                    safePut(new RenderEvent.FinalizeMessage());
                } else {
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
                    safePut(new RenderEvent.FinalizeMessage());
                }
                toolPhase = ToolPhase.IDLE;
            }
            case CLEAR -> {
                deltaBuffer.forceFlush();
                sessionManager.clear();
                safePut(new RenderEvent.ClearChat());
            }
            case EXIT, QUIT -> { handleShutdown(); return true; }
            case HELP -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage("""
                    Commands:
                      /exit       - Exit LavenderCode
                      /clear      - Clear conversation history
                      /help       - Show this help
                    Keyboard:
                      ↑/↓         - Scroll one line
                      PageUp/Down - Scroll one page
                      Home/End    - Jump to top/bottom
                      Mouse wheel - Scroll message area
                      Ctrl+C      - Cancel current request
                      Ctrl+D (empty) - Exit
                      Enter       - Send message
                      Ctrl+J      - Insert newline"""));
            }
            case SCROLL -> {
                deltaBuffer.forceFlush();
                RenderEvent se = parseScrollEvent(cmd.args());
                if (se != null) safePut(se);
            }
        }
        return false;
    }

    private void handleShutdown() {
        cancelTimer();
        RequestContext ctx = currentRequest.getAndSet(null);
        if (ctx != null) chatService.cancel(ctx);
        deltaBuffer.clear();
        toolPhase = ToolPhase.IDLE;

        drainRenderQueue();

        safePut(new RenderEvent.Shutdown());
    }

    private void drainRenderQueue() {
        renderQueue.drainTo(new java.util.ArrayList<>());
    }

    private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
        if (currentRequest.get() != ctx) return;

        // Check for user cancellation during EXECUTING
        if (currentRequest.get() == null && toolPhase == ToolPhase.EXECUTING) {
            completedToolCalls.clear();
            toolPhase = ToolPhase.IDLE;
            safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
            safePut(new RenderEvent.FinalizeMessage());
            return;
        }

        // Delegate to phase-specific handler
        switch (toolPhase) {
            case STREAMING -> handleStreamingPhase(ctx, delta);
            case STREAMING_FINAL -> handleStreamingFinalPhase(ctx, delta);
            case EXECUTING, REINJECTING, DONE, IDLE -> {
                // Process normally (DONE/IDLE transitions handled above)
                handleStreamingPhase(ctx, delta);
            }
        }
    }

    private void handleStreamingPhase(RequestContext ctx, DeltaEvent delta) {
        switch (delta) {
            case DeltaEvent.Content(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, t, 0));
            case DeltaEvent.ToolCallStart tcs -> {
                safePut(new RenderEvent.ToolCallRender(tcs.toolCallId(), tcs.toolName(), Map.of(), "准备中…"));
            }
            case DeltaEvent.ToolCallDelta tcd -> {
                // Accumulated by StreamingChatService, no UI update needed for deltas
            }
            case DeltaEvent.ToolCallEnd tce -> {
                completedToolCalls.add(tce.toolCall());
                // logDebug("[DEBUG] Orchestrator ToolCallEnd: name=" + tce.toolCall().name() + " total=" + (completedToolCalls.size() + 1));
                safePut(new RenderEvent.ToolCallRender(
                    tce.toolCall().id(), tce.toolCall().name(), tce.toolCall().parameters(), "执行中…"));
            }
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(providerName, modelName, null, i + o));
            case DeltaEvent.Complete() -> {
                deltaBuffer.forceFlush();
                if (completedToolCalls.isEmpty()) {
                    // logDebug("[DEBUG] Orchestrator Complete: NO tool calls, total=" + completedToolCalls.size());
                    // No tool calls — finish normally
                    if (currentRequest.compareAndSet(ctx, null)) {
                        cancelTimer();
                        long seconds = currentTimer != null ? currentTimer.elapsedSeconds() : 0;
                        safePut(new RenderEvent.StatusUpdate(
                            providerName, modelName, "Done (" + seconds + "s)", 0));
                        safePut(new RenderEvent.FinalizeMessage());
                        toolPhase = ToolPhase.IDLE;
                        timerScheduler.schedule(() -> safePut(
                            new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                            1, TimeUnit.SECONDS);
                    }
                } else {
                    // Tool calls detected — switch to EXECUTING phase
                    // logDebug("[DEBUG] Orchestrator Complete: HAS tool calls, total=" + completedToolCalls.size() + " -> executing");
                    toolPhase = ToolPhase.EXECUTING;
                    executeAllTools(ctx);
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                    toolPhase = ToolPhase.IDLE;
                }
            }
        }
    }

    private void handleStreamingFinalPhase(RequestContext ctx, DeltaEvent delta) {
        switch (delta) {
            case DeltaEvent.Content(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, t, 0));
            case DeltaEvent.ToolCallStart tcs -> {
                safePut(new RenderEvent.ToolCallRender(tcs.toolCallId(), tcs.toolName(), Map.of(), "准备中…"));
            }
            case DeltaEvent.ToolCallDelta tcd -> {
                // Accumulated by StreamingChatService
            }
            case DeltaEvent.ToolCallEnd tce -> {
                completedToolCalls.add(tce.toolCall());
                // logDebug("[DEBUG] Orchestrator Final phase ToolCallEnd: name=" + tce.toolCall().name() + " round=" + reInjectRound + " total=" + completedToolCalls.size());
                safePut(new RenderEvent.ToolCallRender(
                    tce.toolCall().id(), tce.toolCall().name(), tce.toolCall().parameters(), "执行中…"));
            }
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(providerName, modelName, null, i + o));
            case DeltaEvent.Complete() -> {
                deltaBuffer.forceFlush();
                if (!completedToolCalls.isEmpty() && reInjectRound < MAX_REINJECT_ROUNDS) {
                    // More tool calls — execute and re-inject again
                    // logDebug("[DEBUG] Orchestrator Final Complete: more tool calls round=" + reInjectRound + " total=" + completedToolCalls.size());
                    toolPhase = ToolPhase.EXECUTING;
                    executeAllTools(ctx);
                } else {
                    // No more tool calls or max rounds reached — finish
                    if (reInjectRound >= MAX_REINJECT_ROUNDS) {
                        // logDebug("[DEBUG] Orchestrator Final Complete: max rounds reached");
                        safePut(new RenderEvent.AddSystemMessage("[Max tool rounds reached]"));
                    }
                    if (currentRequest.compareAndSet(ctx, null)) {
                        cancelTimer();
                        pendingToolMsgCount = 0; // re-injection succeeded, keep tool messages in history
                        long seconds = currentTimer != null ? currentTimer.elapsedSeconds() : 0;
                        safePut(new RenderEvent.StatusUpdate(
                            providerName, modelName, "Done (" + seconds + "s)", 0));
                        safePut(new RenderEvent.FinalizeMessage());
                        toolPhase = ToolPhase.IDLE;
                        timerScheduler.schedule(() -> safePut(
                            new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                            1, TimeUnit.SECONDS);
                    }
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                    rollbackToolMessages();
                    toolPhase = ToolPhase.IDLE;
                }
            }
        }
    }

    private void executeAllTools(RequestContext ctx) {
        List<ToolResult> results = new ArrayList<>();
        for (ToolCall tc : completedToolCalls) {
            // Check user cancellation
            if (currentRequest.get() == null) {
                completedToolCalls.clear();
                toolPhase = ToolPhase.IDLE;
                safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
                safePut(new RenderEvent.FinalizeMessage());
                return;
            }

            Tool tool = ToolRegistry.get(tc.name());
            if (tool == null) {
                var err = ToolResult.error("TOOL_NOT_FOUND", "工具未注册·" + tc.name(), tc.name());
                results.add(err);
                safePut(new RenderEvent.ToolResultRender(tc.id(), err.summary(), false, 0));
                continue;
            }

            // Execute with timeout
            long timeout = "execute_command".equals(tc.name())
                ? options.commandTimeoutSeconds() : options.fileOperationTimeoutSeconds();
            ToolResult result = executeWithTimeout(tool, tc.parameters(), timeout);
            results.add(result);

            // Render result
            safePut(new RenderEvent.ToolResultRender(tc.id(), result.summary(), result.success(),
                result.content() != null ? result.content().length() : 0));
        }

        // Check user cancellation again before re-injecting
        if (currentRequest.get() == null) {
            completedToolCalls.clear();
            toolPhase = ToolPhase.IDLE;
            safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
            safePut(new RenderEvent.FinalizeMessage());
            return;
        }

        toolPhase = ToolPhase.REINJECTING;
        reInjectAndContinue(results);
    }

    private ToolResult executeWithTimeout(Tool tool, Map<String, Object> params, long timeoutSec) {
        try {
            return CompletableFuture.supplyAsync(() -> tool.execute(params))
                .orTimeout(timeoutSec, TimeUnit.SECONDS)
                .exceptionally(ex -> ToolResult.error("TIMEOUT",
                    "超时 (" + timeoutSec + "s)·" + tool.name(), ex.getMessage()))
                .get();
        } catch (Exception e) {
            return ToolResult.error("TOOL_ERROR",
                e.getMessage() != null ? e.getMessage() : "未知错误", e.getClass().getSimpleName());
        }
    }

    private void reInjectAndContinue(List<ToolResult> results) {
        int historySizeBefore = sessionManager.getMessageCount();

        // Add tool messages to session history for the re-injection call
        sessionManager.addToolMessages(completedToolCalls, results);
        pendingToolMsgCount = sessionManager.getMessageCount() - historySizeBefore;

        // Clear accumulated tool calls for next round
        completedToolCalls.clear();

        reInjectRound++;
        // logDebug("[DEBUG] Orchestrator reInjectAndContinue round=" + reInjectRound);

        // Build system prompt with agent instructions
        String systemPrompt = AgentPromptBuilder.build(options.systemPrompt());
        var overrideConfig = new LlmConfig(config.providers(), options.withSystemPrompt(systemPrompt));

        // Send follow-up request with updated history
        toolPhase = ToolPhase.STREAMING_FINAL;
        var ctxRef = new AtomicReference<RequestContext>();
        try {
            List<ToolDefinition> toolDefs = ToolRegistry.export();
            ctxRef.set(chatService.submit(provider, sessionManager.getHistory(), overrideConfig, toolDefs,
                delta -> onDeltaReceived(ctxRef.get(), delta)));
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            // Rollback tool messages on failure to prevent history corruption
            rollbackToolMessages();
            toolPhase = ToolPhase.IDLE;
            safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
            safePut(new RenderEvent.FinalizeMessage());
        }
    }

    private void rollbackToolMessages() {
        if (pendingToolMsgCount > 0) {
            sessionManager.removeLastMessages(pendingToolMsgCount);
            pendingToolMsgCount = 0;
        }
    }

    private RenderEvent parseScrollEvent(String args) {
        return switch (args.trim().toLowerCase()) {
            case "up"        -> new RenderEvent.ScrollDelta(-1);
            case "down"      -> new RenderEvent.ScrollDelta(1);
            case "page-up"   -> new RenderEvent.ScrollPageUp();
            case "page-down" -> new RenderEvent.ScrollPageDown();
            case "top"       -> new RenderEvent.ScrollTo(0);
            case "bottom"    -> new RenderEvent.ScrollAutoReset();
            default          -> null;
        };
    }

    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }
    }

    private void safePut(RenderEvent event) {
        try { renderQueue.put(event); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
