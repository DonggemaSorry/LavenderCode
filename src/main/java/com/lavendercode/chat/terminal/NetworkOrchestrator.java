package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextEvent;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.permission.*;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.prompt.SystemPromptAssembler;
import com.lavendercode.core.prompt.EnvironmentInfoCollector;
import com.lavendercode.core.prompt.ToolDescriptionEnhancer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NetworkOrchestrator {

    private static final String APP_VERSION = "1.0.0";

    private final DeltaBuffer deltaBuffer;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final BlockingQueue<InputEvent> inputQueue;
    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String modelName;
    private final LlmConfig config;
    private final Options options;
    private final ScheduledExecutorService timerScheduler;

    private final BatchingToolExecutor batchExecutor;
    private final TokenAccumulator tokenAccumulator = new TokenAccumulator();
    private final PermissionModeManager modeManager;
    private final HitlCoordinator hitlCoordinator;
    private final PermissionPipeline permissionPipeline;
    private final ContextManager contextManager;
    private final String fileInstructions;
    private final Supplier<String> memoryIndexSupplier;
    private final ReentrantLock sessionLock = new ReentrantLock();

    private volatile ReActLoop currentLoop;
    private volatile Thread loopThread;
    private volatile ScheduledFuture<?> timerTask;
    private volatile ResponseTimer currentTimer;

    public NetworkOrchestrator(DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler,
                               Path projectRoot) {
        this(deltaBuffer, renderQueue, inputQueue, sessionManager, provider,
            providerName, modelName, config, timerScheduler, projectRoot,
            com.lavendercode.core.context.NoOpContextManager.INSTANCE);
    }

    public NetworkOrchestrator(DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler,
                               Path projectRoot,
                               ContextManager contextManager) {
        this(deltaBuffer, renderQueue, inputQueue, sessionManager, provider,
            providerName, modelName, config, timerScheduler, projectRoot, contextManager, "", () -> "");
    }

    public NetworkOrchestrator(DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler,
                               Path projectRoot,
                               ContextManager contextManager,
                               String fileInstructions,
                               Supplier<String> memoryIndexSupplier) {
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.options = config.options();
        this.timerScheduler = timerScheduler;

        PermissionConfig permConfig = PermissionConfigLoader.load(
            projectRoot,
            Path.of(System.getProperty("user.home")).resolve(".lavendercode"));
        this.modeManager = new PermissionModeManager(permConfig.defaultMode());
        this.hitlCoordinator = new HitlCoordinator(renderQueue);
        AtomicReference<RuleEngineLayer> ruleRef = new AtomicReference<>();
        Consumer<List<PermissionRule>> reloadLocal = rules ->
            ruleRef.set(RuleEngineLayer.fromTiers(rules, permConfig.projectRules(), permConfig.userRules()));
        this.permissionPipeline = PermissionPipeline.create(
            permConfig, modeManager::getMode, hitlCoordinator, projectRoot, reloadLocal);
        this.batchExecutor = new BatchingToolExecutor(
            options.fileOperationTimeoutSeconds(),
            options.commandTimeoutSeconds(),
            permissionPipeline,
            projectRoot);
        this.contextManager = contextManager != null ? contextManager : com.lavendercode.core.context.NoOpContextManager.INSTANCE;
        this.contextManager.setEventSink(this::onContextEvent);
        this.fileInstructions = fileInstructions != null ? fileInstructions : "";
        this.memoryIndexSupplier = memoryIndexSupplier != null ? memoryIndexSupplier : () -> "";
    }

    private void onContextEvent(ContextEvent event) {
        switch (event) {
            case ContextEvent.Compacting c ->
                safePut(new RenderEvent.AddSystemMessage("[" + c.message() + "]"));
            case ContextEvent.Compacted c ->
                safePut(new RenderEvent.AddSystemMessage(
                    "[已压缩,token 从 " + c.tokensBefore() + " 降至 " + c.tokensAfter() + "]"));
            case ContextEvent.CompactFailed f ->
                safePut(new RenderEvent.AddSystemMessage("[压缩失败: " + f.reason() + "]"));
        }
    }

    public HitlCoordinator hitlCoordinator() {
        return hitlCoordinator;
    }

    public void run() {
        safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", 0));
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ExecuteCommand cmd -> {
                        if (handleCommand(cmd)) {
                            return;
                        }
                    }
                    case InputEvent.CyclePermissionMode __ -> {
                        modeManager.cycleMode();
                        int tokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                        safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", tokens));
                    }
                    case InputEvent.HitlChoice hc -> hitlCoordinator.complete(hc.choice());
                    case InputEvent.Shutdown __ -> {
                        handleShutdown();
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSendMessage(InputEvent.SendMessage msg) {
        if (currentLoop != null) {
            return;
        }

        tokenAccumulator.reset();
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));

        String stablePrompt = SystemPromptAssembler.assemble(
            options.systemPrompt(), fileInstructions, memoryIndexSupplier.get());
        String envInfo = EnvironmentInfoCollector.collect(modelName, APP_VERSION);
        List<ToolDefinition> toolDefs = ToolDescriptionEnhancer.enhance(
            modeManager.getToolDefinitions(options.toolSystemEnabled()));

        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3, contextManager);
        loop.setConfig(config, toolDefs, stablePrompt, envInfo, modeManager);
        currentLoop = loop;

        startTimer();

        final ReActLoop loopRef = loop;
        loopThread = new Thread(() -> {
            sessionLock.lock();
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
                sessionLock.unlock();
                if (!timerScheduler.isShutdown()) {
                    int finalTokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                    timerScheduler.schedule(() -> safePut(
                        new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", finalTokens)),
                        1, TimeUnit.SECONDS);
                }
            }
        }, "lavender-react-loop");
        loopThread.start();
    }

    private void onAgentEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.RoundStart rs ->
                safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName,
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
                    modeManager.getMode().label(), modelName, null,
                    u.inputTokens() + u.outputTokens()));
            case AgentEvent.RoundEnd re -> { /* no-op */ }
            case AgentEvent.Complete c -> {
                deltaBuffer.forceFlush();
                stopTimer();
                long seconds = currentTimer != null ? currentTimer.elapsedSeconds() : 0;
                int finalTokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                safePut(new RenderEvent.StatusUpdate(
                    modeManager.getMode().label(), modelName, "Done (" + seconds + "s)", finalTokens));
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
            }
            case PLAN -> {
                modeManager.enterPlanMode();
                int tokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", tokens));
                safePut(new RenderEvent.AddSystemMessage(
                    "[已进入计划模式 · 仅只读工具可用]"));
            }
            case DO -> {
                modeManager.exitPlanToDefault();
                int tokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", tokens));
                safePut(new RenderEvent.AddSystemMessage(
                    "[已退出计划模式 · 所有工具可用]"));
                handleSendMessage(new InputEvent.SendMessage("请根据以上计划开始执行"));
            }
            case CLEAR -> {
                deltaBuffer.forceFlush();
                sessionManager.clear();
                safePut(new RenderEvent.ClearChat());
            }
            case EXIT, QUIT -> {
                handleShutdown();
                return true;
            }
            case HELP -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage(BuiltinCommandRegistry.helpText()));
            }
            case COMPACT -> handleCompact();
            case UNKNOWN -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage("[" + cmd.args() + "]"));
                safePut(new RenderEvent.FinalizeMessage());
            }
            case SCROLL -> {
                deltaBuffer.forceFlush();
                RenderEvent se = parseScrollEvent(cmd.args());
                if (se != null) {
                    safePut(se);
                }
            }
        }
        return false;
    }

    private void handleCompact() {
        sessionLock.lock();
        try {
            List<ToolDefinition> toolDefs = ToolDescriptionEnhancer.enhance(
                modeManager.getToolDefinitions(options.toolSystemEnabled()));
            var result = contextManager.runCompaction(CompactTrigger.MANUAL, toolDefs);
            if (result.success()) {
                safePut(new RenderEvent.AddSystemMessage(
                    "[手动压缩完成: " + result.tokensBefore() + " → " + result.tokensAfter() + " tokens]"));
            } else if (result.error() != null) {
                safePut(new RenderEvent.AddSystemMessage("[压缩失败: " + result.error() + "]"));
            }
            safePut(new RenderEvent.FinalizeMessage());
        } finally {
            sessionLock.unlock();
        }
    }

    private void handleShutdown() {
        if (currentLoop != null) {
            currentLoop.cancel();
        }
        if (loopThread != null) {
            try {
                loopThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
        if (timerScheduler.isShutdown()) {
            return;
        }
        final ResponseTimer timer = new ResponseTimer();
        timer.start();
        this.currentTimer = timer;
        try {
            this.timerTask = timerScheduler.scheduleAtFixedRate(() -> {
                int tokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                safePut(new RenderEvent.StatusUpdate(
                    modeManager.getMode().label(), modelName,
                    "Imagining\u2026 (" + timer.elapsedSeconds() + "s)", tokens));
            }, 1, 1, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // Scheduler may already be shut down during test teardown
        }
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
        try {
            renderQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
