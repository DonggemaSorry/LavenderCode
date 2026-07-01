package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NetworkOrchestrator {

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

    // ReAct loop components
    private final BatchingToolExecutor batchExecutor;
    private final TokenAccumulator tokenAccumulator = new TokenAccumulator();
    private final PlanModeManager planMode = new PlanModeManager();

    // Runtime state
    private volatile ReActLoop currentLoop;
    private volatile Thread loopThread;
    private volatile ScheduledFuture<?> timerTask;
    private volatile ResponseTimer currentTimer;

    public NetworkOrchestrator(DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler) {
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
        this.batchExecutor = new BatchingToolExecutor(
            options.fileOperationTimeoutSeconds(),
            options.commandTimeoutSeconds()
        );
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
        // Ignore if loop already running
        if (currentLoop != null) return;

        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));

        // Build system prompt based on mode
        String systemPrompt = planMode.getSystemPrompt(options.systemPrompt());
        var promptConfig = new LlmConfig(config.providers(), options.withSystemPrompt(systemPrompt));
        List<ToolDefinition> toolDefs = options.toolSystemEnabled()
            ? planMode.getToolDefinitions() : List.of();

        // Create and configure loop
        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3);
        loop.setConfig(promptConfig, toolDefs);
        currentLoop = loop;

        // Start timer
        startTimer();

        // Run loop in background thread
        final ReActLoop loopRef = loop;
        loopThread = new Thread(() -> {
            try {
                loopRef.run(msg.text(), NetworkOrchestrator.this::onAgentEvent);
            } catch (Exception e) {
                deltaBuffer.forceFlush();
                stopTimer();
                safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
                safePut(new RenderEvent.FinalizeMessage());
            } finally {
                stopTimer();
                currentLoop = null;
                loopThread = null;
                timerScheduler.schedule(() -> safePut(
                    new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                    1, TimeUnit.SECONDS);
            }
        }, "lavender-react-loop");
        loopThread.start();
    }

    private void onAgentEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.RoundStart rs ->
                safePut(new RenderEvent.StatusUpdate(providerName, modelName,
                    "Round " + rs.round() + " …",
                    tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput()));
            case AgentEvent.Content c ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(
                    DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, c.text(), 0));
            case AgentEvent.ToolCallStart tcs ->
                safePut(new RenderEvent.ToolCallRender(
                    tcs.toolCallId(), tcs.toolName(), Map.of(), "准备中…"));
            case AgentEvent.ToolCallEnd tce -> {
                var tc = tce.toolCall();
                safePut(new RenderEvent.ToolCallRender(
                    tc.id(), tc.name(), tc.parameters(), "执行中…"));
            }
            case AgentEvent.ToolResultReady trr ->
                safePut(new RenderEvent.ToolResultRender(
                    trr.toolCallId(), trr.result().summary(),
                    trr.result().success(),
                    trr.result().content() != null ? trr.result().content().length() : 0));
            case AgentEvent.Usage u ->
                safePut(new RenderEvent.StatusUpdate(
                    providerName, modelName, null,
                    u.inputTokens() + u.outputTokens()));
            case AgentEvent.RoundEnd re -> { /* no-op */ }
            case AgentEvent.Complete c -> {
                deltaBuffer.forceFlush();
                stopTimer();
                long seconds = currentTimer != null ? currentTimer.elapsedSeconds() : 0;
                safePut(new RenderEvent.StatusUpdate(
                    providerName, modelName, "Done (" + seconds + "s)", 0));
                safePut(new RenderEvent.FinalizeMessage());
            }
            case AgentEvent.Stopped s -> {
                deltaBuffer.forceFlush();
                stopTimer();
                safePut(new RenderEvent.AddSystemMessage("[" + s.message() + "]"));
                safePut(new RenderEvent.FinalizeMessage());
            }
            case AgentEvent.Error e -> {
                deltaBuffer.forceFlush();
                stopTimer();
                safePut(new RenderEvent.AddSystemMessage("[Error] " + e.message()));
                safePut(new RenderEvent.FinalizeMessage());
            }
        }
    }

    private boolean handleCommand(InputEvent.ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
                if (currentLoop != null) {
                    currentLoop.cancel();
                } else {
                    safePut(new RenderEvent.AddSystemMessage("[No active request]"));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
            case ESC_CANCEL -> {
                if (currentLoop != null) {
                    currentLoop.cancel();
                }
                // If idle, ignore
            }
            case PLAN -> {
                planMode.enterPlanMode();
                safePut(new RenderEvent.AddSystemMessage(
                    "[已进入计划模式 · 仅只读工具可用]"));
            }
            case DO -> {
                planMode.exitToDo();
                safePut(new RenderEvent.AddSystemMessage(
                    "[已退出计划模式 · 所有工具可用]"));
                handleSendMessage(new InputEvent.SendMessage("请根据以上计划开始执行"));
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
                      /plan       - Enter plan mode (read-only tools only)
                      /do         - Exit plan mode and execute plan
                      /cancel     - Cancel current request
                    Keyboard:
                      ↑/↓         - Scroll one line
                      PageUp/Down - Scroll one page
                      Home/End    - Jump to top/bottom
                      Mouse wheel - Scroll message area
                      Esc         - Cancel current request (don't exit)
                      Ctrl+C      - Exit LavenderCode
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
        if (currentLoop != null) {
            currentLoop.cancel();
        }
        if (loopThread != null) {
            try { loopThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        stopTimer();
        deltaBuffer.clear();
        drainRenderQueue();
        safePut(new RenderEvent.Shutdown());
    }

    private void drainRenderQueue() {
        renderQueue.drainTo(new java.util.ArrayList<>());
    }

    private void startTimer() {
        final ResponseTimer timer = new ResponseTimer();
        timer.start();
        this.currentTimer = timer;
        this.timerTask = timerScheduler.scheduleAtFixedRate(() -> {
            safePut(new RenderEvent.StatusUpdate(
                providerName, modelName,
                "Imagining\u2026 (" + timer.elapsedSeconds() + "s)", 0));
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
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

    private void safePut(RenderEvent event) {
        try { renderQueue.put(event); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
