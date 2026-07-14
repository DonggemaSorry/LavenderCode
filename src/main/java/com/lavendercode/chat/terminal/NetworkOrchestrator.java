package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.PersistingSessionManager;
import com.lavendercode.chat.session.RestoreResult;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.session.SessionRestorer;
import com.lavendercode.chat.session.SessionTranscriptWriter;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextEvent;
import com.lavendercode.core.context.ContextWindowDefaults;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.context.SessionHandle;
import com.lavendercode.core.context.TokenEstimator;
import com.lavendercode.core.hook.HookConfig;
import com.lavendercode.core.hook.HookConfigLoader;
import com.lavendercode.core.hook.HookEngine;
import com.lavendercode.core.hook.HookEngineImpl;
import com.lavendercode.core.hook.HookEvent;
import com.lavendercode.core.hook.HookPayload;
import com.lavendercode.core.memory.MemoryService;
import com.lavendercode.core.permission.*;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.prompt.SystemPromptAssembler;
import com.lavendercode.core.prompt.EnvironmentInfoCollector;
import com.lavendercode.core.prompt.ToolDescriptionEnhancer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

    final DeltaBuffer deltaBuffer;
    final BlockingQueue<RenderEvent> renderQueue;
    final BlockingQueue<InputEvent> inputQueue;
    final SessionManager sessionManager;
    private final LlmProvider provider;
    final String modelName;
    private final LlmConfig config;
    final Options options;
    private final ScheduledExecutorService timerScheduler;
    final Path projectRoot;
    final SessionHandle handle;

    private final BatchingToolExecutor batchExecutor;
    final TokenAccumulator tokenAccumulator = new TokenAccumulator();
    final PermissionModeManager modeManager;
    private final HitlCoordinator hitlCoordinator;
    private final PermissionPipeline permissionPipeline;
    final ContextManager contextManager;
    private final String fileInstructions;
    private final Supplier<String> memoryIndexSupplier;
    final MemoryService memoryService;
    private final ReentrantLock sessionLock = new ReentrantLock();
    private HookEngineImpl hookEngine;

    public HookEngine hookEngine() { return hookEngine; }

    volatile ReActLoop currentLoop;
    private volatile Thread loopThread;
    private volatile ScheduledFuture<?> timerTask;
    private volatile ResponseTimer currentTimer;
    private volatile boolean resuming;
    volatile int turnCount;
    private volatile String lastUserMessage = "";

    private com.lavendercode.core.command.CommandRegistry commandRegistry;
    private com.lavendercode.core.command.CommandContext commandContext;

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
            providerName, modelName, config, timerScheduler, projectRoot, contextManager,
            "", new MemoryService(projectRoot, Path.of(System.getProperty("user.home"))));
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
        this(deltaBuffer, renderQueue, inputQueue, sessionManager, provider,
            providerName, modelName, config, timerScheduler, projectRoot, contextManager,
            fileInstructions, memoryIndexSupplier, null);
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
                               MemoryService memoryService) {
        this(deltaBuffer, renderQueue, inputQueue, sessionManager, provider,
            providerName, modelName, config, timerScheduler, projectRoot, contextManager,
            fileInstructions, memoryService, null);
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
                               Supplier<String> memoryIndexSupplier,
                               SessionHandle handle) {
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.options = config.options();
        this.timerScheduler = timerScheduler;
        this.projectRoot = projectRoot;
        this.handle = handle;

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
        this.memoryService = null;
        initHookEngine();
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
                               MemoryService memoryService,
                               SessionHandle handle) {
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.options = config.options();
        this.timerScheduler = timerScheduler;
        this.projectRoot = projectRoot;
        this.handle = handle;

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
        this.memoryService = memoryService != null
            ? memoryService
            : new MemoryService(projectRoot, Path.of(System.getProperty("user.home")));
        this.memoryIndexSupplier = this.memoryService::currentIndex;
        initHookEngine();
    }

    private void initHookEngine() {
        HookConfig hookConfig = new HookConfigLoader().load(projectRoot);
        this.hookEngine = new HookEngineImpl(hookConfig);
    }

    private void dispatchHook(HookEvent event, HookPayload payload) {
        if (hookEngine != null) {
            hookEngine.dispatch(event, payload, new AtomicBoolean(false));
        }
    }

    private HookPayload sessionPayload(HookEvent event) {
        return HookPayload.builder(event)
            .sessionId(handle != null ? handle.sessionId() : "")
            .cwd(projectRoot)
            .mode(modeManager.getMode().label())
            .build();
    }

    private void onContextEvent(ContextEvent event) {
        switch (event) {
            case ContextEvent.Compacting c -> {
                dispatchHook(HookEvent.PreCompact, sessionPayload(HookEvent.PreCompact));
                safePut(new RenderEvent.AddSystemMessage("[" + c.message() + "]"));
            }
            case ContextEvent.Compacted c -> {
                safePut(new RenderEvent.AddSystemMessage(
                    "[已压缩,token 从 " + c.tokensBefore() + " 降至 " + c.tokensAfter() + "]"));
                var payload = HookPayload.builder(HookEvent.PostCompact)
                    .sessionId(handle != null ? handle.sessionId() : "")
                    .cwd(projectRoot)
                    .mode(modeManager.getMode().label())
                    .put("tokens_before", c.tokensBefore())
                    .put("tokens_after", c.tokensAfter())
                    .build();
                dispatchHook(HookEvent.PostCompact, payload);
            }
            case ContextEvent.CompactFailed f ->
                safePut(new RenderEvent.AddSystemMessage("[压缩失败: " + f.reason() + "]"));
        }
    }

    public HitlCoordinator hitlCoordinator() {
        return hitlCoordinator;
    }

    public boolean isAgentRunning() {
        return currentLoop != null;
    }

    public boolean isResuming() {
        return resuming;
    }

    public void run() {
        safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", 0));
        dispatchHook(HookEvent.SessionStart, sessionPayload(HookEvent.SessionStart));
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ResumeSession resume -> handleResumeSession(resume.sessionId());
                    case InputEvent.ExecuteCommand cmd -> {
                        if (handleCommand(cmd.rawInput())) {
                            return;
                        }
                    }
                    case InputEvent.CyclePermissionMode __ -> {
                        modeManager.cycleMode();
                        int tokens = tokenAccumulator.getTotalInput() + tokenAccumulator.getTotalOutput();
                        safePut(new RenderEvent.StatusUpdate(modeManager.getMode().label(), modelName, "", tokens));
                    }
                    case InputEvent.HitlChoice hc -> hitlCoordinator.complete(hc.choice());
                    case InputEvent.CancelAgent __ -> {
                        if (currentLoop != null) {
                            currentLoop.cancel();
                        }
                    }
                    case InputEvent.ScrollEvent se -> {
                        RenderEvent scrollEvent = parseScrollEvent(se.command());
                        if (scrollEvent != null) {
                            safePut(scrollEvent);
                        }
                    }
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

    public void bindCommandSystem(com.lavendercode.core.command.CommandRegistry registry,
                                   com.lavendercode.core.command.CommandContext context) {
        this.commandRegistry = registry;
        this.commandContext = context;
    }

    void handleSendMessage(InputEvent.SendMessage msg) {
        if (currentLoop != null || resuming) {
            return;
        }

        tokenAccumulator.reset();
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));
        lastUserMessage = msg.text();

        String stablePrompt = SystemPromptAssembler.assemble(
            options.systemPrompt(), fileInstructions, currentMemoryIndex());
        String envInfo = EnvironmentInfoCollector.collect(modelName, APP_VERSION);
        List<ToolDefinition> toolDefs = ToolDescriptionEnhancer.enhance(
            modeManager.getToolDefinitions(options.toolSystemEnabled()));

        var loop = new ReActLoop(provider, sessionManager, batchExecutor, tokenAccumulator, 10, 3, contextManager);
        loop.setConfig(config, toolDefs, stablePrompt, envInfo, modeManager);
        if (hookEngine != null) loop.setHookEngine(hookEngine);
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
                turnCount++;
                if (memoryService != null) {
                    memoryService.maybeUpdateAsync(provider, config, sessionManager.getHistory(), turnCount, lastUserMessage);
                }
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

    private String currentMemoryIndex() {
        return memoryService != null ? memoryService.currentIndex() : memoryIndexSupplier.get();
    }

    void handleResumeSession(String sessionId) {
        String blocked = ResumeGate.check(isAgentRunning(), resuming);
        if (blocked != null) {
            safePut(new RenderEvent.AddSystemMessage("[" + blocked + "]"));
            safePut(new RenderEvent.FinalizeMessage());
            return;
        }

        resuming = true;
        sessionLock.lock();
        try {
            Path jsonl = projectRoot.resolve(".lavendercode/sessions")
                .resolve(sessionId)
                .resolve("conversation.jsonl");
            RestoreResult result = SessionRestorer.restore(
                jsonl, contextManager, contextWindow(), new TokenEstimator());

            String reminder = result.timeSpanReminderOrNull();
            List<Message> messagesForHistory = withoutTrailingReminder(result.messages(), reminder);

            if (sessionManager instanceof PersistingSessionManager persisting && handle != null) {
                persisting.suspendPersistence();
                try {
                    persisting.replaceHistory(messagesForHistory);
                } finally {
                    persisting.resumePersistence();
                }
                handle.rebind(sessionId);
                persisting.swapWriter(SessionTranscriptWriter.open(handle.paths().conversationJsonl()));
                if (reminder != null) {
                    persisting.addUserMessage(reminder);
                }
            } else {
                sessionManager.replaceHistory(result.messages());
            }

            if (hookEngine != null) {
                dispatchHook(HookEvent.SessionEnd, sessionPayload(HookEvent.SessionEnd));
                hookEngine.clearOnce();
            }
            boolean compacted = result.compacted()
                || SessionRestorer.maybeCompact(sessionManager, contextManager, contextWindow(), new TokenEstimator());
            int count = sessionManager.getMessageCount();
            safePut(new RenderEvent.AddSystemMessage(
                "[已恢复会话 " + sessionId + "，共 " + count + " 条消息" + (compacted ? "，已压缩上下文" : "") + "]"));
            safePut(new RenderEvent.FinalizeMessage());
            dispatchHook(HookEvent.SessionResume, sessionPayload(HookEvent.SessionResume));
        } catch (IOException e) {
            safePut(new RenderEvent.AddSystemMessage("[恢复会话失败: " + e.getMessage() + "]"));
            safePut(new RenderEvent.FinalizeMessage());
        } finally {
            sessionLock.unlock();
            resuming = false;
        }
    }

    private static List<Message> withoutTrailingReminder(List<Message> messages, String reminder) {
        if (reminder == null || messages.isEmpty()) {
            return messages;
        }
        Message last = messages.get(messages.size() - 1);
        if (reminder.equals(last.content())) {
            return List.copyOf(messages.subList(0, messages.size() - 1));
        }
        return messages;
    }

    private int contextWindow() {
        for (ProviderConfig providerConfig : config.providers()) {
            if (modelName.equals(providerConfig.model())) {
                return ContextWindowDefaults.resolve(providerConfig.protocol(), providerConfig.contextWindow());
            }
        }
        String protocol = provider != null ? provider.protocol() : "";
        return ContextWindowDefaults.resolve(protocol, null);
    }

    private boolean handleCommand(String rawInput) {
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) return false;
        String body = trimmed.substring(1).trim();
        String[] parts = body.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : null;
        var found = commandRegistry.find(cmdName);
        if (found.isEmpty()) {
            deltaBuffer.forceFlush();
            safePut(new RenderEvent.AddSystemMessage(
                "未知命令: /" + cmdName + "。输入 /help 查看可用命令"));
            safePut(new RenderEvent.FinalizeMessage());
            return false;
        }
        var def = found.get();
        var kind = def.metadata().kind();
        if (kind != com.lavendercode.core.command.CommandKind.LOCAL && currentLoop != null) {
            safePut(new RenderEvent.AddSystemMessage("[请等待当前任务完成]"));
            safePut(new RenderEvent.FinalizeMessage());
            return false;
        }
        try {
            String result = def.handler().execute(commandContext, args);
            if (result != null && kind == com.lavendercode.core.command.CommandKind.PROMPT) {
                commandContext.injectUserMessage(result);
                if (def.metadata().description() != null
                    && def.metadata().description().contains("[skill]")) {
                    safePut(new RenderEvent.AddSystemMessage(
                        "Successfully loaded skill: " + def.metadata().name()));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
        } catch (Exception e) {
            safePut(new RenderEvent.AddSystemMessage(
                "[命令执行异常: " + e.getMessage() + "]"));
            safePut(new RenderEvent.FinalizeMessage());
        }
        return kind == com.lavendercode.core.command.CommandKind.UI
            && def.metadata().name().equals("exit");
    }

    void handleCompact() {
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

    void handleShutdown() {
        dispatchHook(HookEvent.SessionEnd, sessionPayload(HookEvent.SessionEnd));
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

    RenderEvent parseScrollEvent(String args) {
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

    void safePut(RenderEvent event) {
        try {
            renderQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
