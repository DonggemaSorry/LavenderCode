package com.lavendercode.core.context;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.LlmProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class ContextBootstrap {
    private ContextBootstrap() {}

    public static ContextManager create(Path projectRoot,
                                        ProviderConfig providerConfig,
                                        SessionManager sessionManager,
                                        LlmProvider llmProvider,
                                        LlmConfig llmConfig,
                                        Consumer<ContextEvent> eventSink) throws IOException {
        String sessionId = SessionIdGenerator.generate();
        SessionPaths paths = new SessionPaths(projectRoot, sessionId);
        paths.ensureDirectories();

        int contextWindow = ContextWindowDefaults.resolve(
            providerConfig.protocol(), providerConfig.contextWindow());

        ReplacementLedger ledger = new ReplacementLedger();
        Layer1Offloader layer1 = new Layer1Offloader(sessionManager, paths, ledger);
        TokenEstimator tokenEstimator = new TokenEstimator();
        FileReadTracker fileReadTracker = new FileReadTracker();
        AutoCompactCircuitBreaker circuitBreaker = new AutoCompactCircuitBreaker();
        CompactionService compactionService = new CompactionService(
            sessionManager, llmProvider, llmConfig, layer1,
            tokenEstimator, fileReadTracker, circuitBreaker, contextWindow);
        compactionService.setEventSink(eventSink);

        DefaultContextManager manager = new DefaultContextManager(
            sessionManager, layer1, compactionService, tokenEstimator,
            fileReadTracker, contextWindow, circuitBreaker);
        manager.setEventSink(eventSink);
        return manager;
    }
}
