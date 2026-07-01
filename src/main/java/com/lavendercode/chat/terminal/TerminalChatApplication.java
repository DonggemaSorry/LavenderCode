package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import org.jline.terminal.Terminal;

import java.util.concurrent.*;

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

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme) {
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.providerName = providerName;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
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
        NetworkOrchestrator orchestrator = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, providerName, modelName, config,
            timerScheduler
        );
        InputAreaLayout inputLayout = new InputAreaLayout();
        TerminalRenderer renderer = new TerminalRenderer(
            terminal, renderQueue, theme, providerName, modelName, inputLayout);
        InputSystem inputSystem = new InputSystem(terminal, inputQueue, renderQueue, inputLayout);

        // Threads
        Thread inputThread = new Thread(inputSystem::run, "lavender-input");
        Thread networkThread = new Thread(orchestrator::run, "lavender-network");
        Thread renderThread = new Thread(renderer::run, "lavender-render");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            inputSystem.requestShutdown();
            try { renderQueue.put(new RenderEvent.Shutdown()); } catch (InterruptedException ignored) {}
        }));

        inputThread.start();
        networkThread.start();
        renderThread.start();

        // Wait for render thread to finish (it exits on Shutdown event)
        renderThread.join();

        shutdownWorkers(inputSystem, inputQueue, inputThread, networkThread, timerScheduler, provider);
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
}
