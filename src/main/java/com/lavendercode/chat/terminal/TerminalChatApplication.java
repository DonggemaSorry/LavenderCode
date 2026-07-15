package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.context.SessionHandle;
import com.lavendercode.core.memory.MemoryService;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.skill.SkillCatalog;
import org.jline.terminal.Terminal;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Orchestrates the four-thread terminal chat application lifecycle.
 */
public class TerminalChatApplication {

    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String providerName;
    private final String modelName;
    private final LlmConfig config;
    private final Theme theme;
    private final Path projectRoot;
    private final ContextManager contextManager;
    private final SessionHandle sessionHandle;
    private final Closeable closeOnShutdown;
    private final String fileInstructions;
    private final Supplier<String> memoryIndexSupplier;
    private final MemoryService memoryService;
    private final SkillCatalog skillCatalog;

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            com.lavendercode.core.context.NoOpContextManager.INSTANCE, null);
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot, contextManager, null);
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager,
                                   Closeable closeOnShutdown) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            contextManager, null, closeOnShutdown, "", () -> "");
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager,
                                   Closeable closeOnShutdown,
                                   String fileInstructions,
                                   Supplier<String> memoryIndexSupplier) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            contextManager, null, closeOnShutdown, fileInstructions, memoryIndexSupplier, null, null);
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager,
                                   Closeable closeOnShutdown,
                                   String fileInstructions,
                                   MemoryService memoryService) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            contextManager, null, closeOnShutdown, fileInstructions,
            memoryService != null ? memoryService::currentIndex : () -> "",
            memoryService, null);
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager,
                                   SessionHandle sessionHandle,
                                   Closeable closeOnShutdown,
                                   String fileInstructions,
                                   Supplier<String> memoryIndexSupplier) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            contextManager, sessionHandle, closeOnShutdown, fileInstructions, memoryIndexSupplier, null, null);
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme,
                                   Path projectRoot,
                                   ContextManager contextManager,
                                   SessionHandle sessionHandle,
                                   Closeable closeOnShutdown,
                                   String fileInstructions,
                                   MemoryService memoryService,
                                   SkillCatalog skillCatalog) {
        this(sessionManager, provider, providerName, modelName, config, theme, projectRoot,
            contextManager, sessionHandle, closeOnShutdown, fileInstructions,
            memoryService != null ? memoryService::currentIndex : () -> "",
            memoryService, skillCatalog);
    }

    private TerminalChatApplication(SessionManager sessionManager,
                                    LlmProvider provider,
                                    String providerName,
                                    String modelName,
                                    LlmConfig config,
                                    Theme theme,
                                    Path projectRoot,
                                    ContextManager contextManager,
                                    SessionHandle sessionHandle,
                                    Closeable closeOnShutdown,
                                    String fileInstructions,
                                    Supplier<String> memoryIndexSupplier,
                                    MemoryService memoryService,
                                    SkillCatalog skillCatalog) {
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.providerName = providerName;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
        this.projectRoot = projectRoot;
        this.contextManager = contextManager != null ? contextManager : com.lavendercode.core.context.NoOpContextManager.INSTANCE;
        this.sessionHandle = sessionHandle;
        this.closeOnShutdown = closeOnShutdown;
        this.fileInstructions = fileInstructions != null ? fileInstructions : "";
        this.memoryIndexSupplier = memoryIndexSupplier != null ? memoryIndexSupplier : () -> "";
        this.memoryService = memoryService;
        this.skillCatalog = skillCatalog;
    }

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme) {
        this(sessionManager, provider, providerName, modelName, config, theme,
            Path.of("").toAbsolutePath().normalize());
    }

    public void run(Terminal terminal) throws Exception {
        BlockingQueue<InputEvent> inputQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RenderEvent> renderQueue = new LinkedBlockingQueue<>();

        ScheduledExecutorService timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lavender-timer");
            t.setDaemon(true);
            return t;
        });

        // Components
        DeltaBuffer deltaBuffer = new DeltaBuffer(timerScheduler, renderQueue);
        NetworkOrchestrator orchestrator = memoryService != null
            ? new NetworkOrchestrator(
                deltaBuffer, renderQueue, inputQueue,
                sessionManager, provider, providerName, modelName, config,
                timerScheduler, projectRoot, contextManager, fileInstructions,
                memoryService,
                sessionHandle
            )
            : new NetworkOrchestrator(
                deltaBuffer, renderQueue, inputQueue,
                sessionManager, provider, providerName, modelName, config,
                timerScheduler, projectRoot, contextManager, fileInstructions,
                memoryIndexSupplier,
                sessionHandle
            );
        orchestrator.registerSubAgentTools();
        var defs = new java.util.ArrayList<>(BuiltinCommandRegistrar.builtinCommands());
        if (skillCatalog != null) {
            var existingNames = new java.util.HashSet<String>();
            for (var d : defs) existingNames.add(d.metadata().name());
            var skillHost = new SkillHostImpl(orchestrator);
            var skillForkHost = new SkillForkHostImpl(orchestrator, orchestrator.subAgentServices());
            defs.addAll(SkillCommandWiring.buildSkillCommands(
                skillCatalog, existingNames, skillHost, skillForkHost));
        }
        var registry = new com.lavendercode.core.command.CommandRegistry(defs);
        BuiltinCommandRegistrar.bindRegistry(registry);
        var cmdCtx = new CommandContextImpl(orchestrator, terminal, projectRoot.resolve(".lavendercode/sessions"));
        orchestrator.bindCommandSystem(registry, cmdCtx);
        InputAreaLayout inputLayout = new InputAreaLayout();
        TerminalRenderer renderer = new TerminalRenderer(
            terminal, renderQueue, theme, providerName, modelName, inputLayout);
        InputSystem inputSystem = new InputSystem(
            terminal, inputQueue, renderQueue, inputLayout, orchestrator.hitlCoordinator(),
            orchestrator, projectRoot.resolve(".lavendercode/sessions"),
            registry);

        // Threads
        Thread inputThread = new Thread(inputSystem::run, "lavender-input");
        Thread networkThread = new Thread(orchestrator::run, "lavender-network");
        Thread renderThread = new Thread(renderer::run, "lavender-render");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            inputSystem.requestShutdown();
            try { renderQueue.put(new RenderEvent.Shutdown()); } catch (InterruptedException ignored) {}
        }));

        try {
            inputThread.start();
            networkThread.start();
            renderThread.start();

            // Wait for render thread to finish (it exits on Shutdown event)
            renderThread.join();
        } finally {
            try {
                shutdownWorkers(inputSystem, inputQueue, inputThread, networkThread, timerScheduler, provider);
            } finally {
                closeQuietly(closeOnShutdown);
            }
        }
    }

    private static void shutdownWorkers(InputSystem inputSystem,
                                        BlockingQueue<InputEvent> inputQueue,
                                        Thread inputThread,
                                        Thread networkThread,
                                        ScheduledExecutorService timerScheduler,
                                        LlmProvider provider) throws InterruptedException {
        inputSystem.requestShutdown();
        inputQueue.offer(new InputEvent.Shutdown());

        networkThread.interrupt();
        networkThread.join(100);
        inputThread.interrupt();
        inputThread.join(100);

        timerScheduler.shutdownNow();
        try {
            provider.close();
        } catch (Exception ignored) {
            // Provider cleanup is best-effort during shutdown
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Shutdown cleanup is best-effort.
        }
    }
}
