package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.context.CompactResult;
import com.lavendercode.core.context.CompactTrigger;
import com.lavendercode.core.context.ContextManager;
import com.lavendercode.core.provider.LlmProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NetworkOrchestratorCompactTest {
    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private SessionManager sessionManager;
    private LlmProvider provider;
    private ContextManager contextManager;
    private NetworkOrchestrator orchestrator;

    @TempDir
    Path projectRoot;

    @BeforeEach
    void setUp() {
        inputQueue = new LinkedBlockingQueue<>();
        renderQueue = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        sessionManager = new InMemorySessionManager();
        provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");
        contextManager = mock(ContextManager.class);
        when(contextManager.runCompaction(eq(CompactTrigger.MANUAL), anyList()))
            .thenReturn(CompactResult.ok(80_000, 30_000));

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai-compatible", "openai-compatible", "gpt-4", "http://localhost", "key", null)),
            null);

        orchestrator = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot, contextManager
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        try {
            inputQueue.put(new InputEvent.Shutdown());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private RenderEvent pollUntil(Class<? extends RenderEvent> type) throws InterruptedException {
        RenderEvent event;
        while ((event = renderQueue.poll(2, TimeUnit.SECONDS)) != null) {
            if (type.isInstance(event)) return event;
        }
        return null;
    }

    @Test
    void compactCommandInvokesManualCompaction() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.COMPACT, ""));

        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("手动压缩完成");
        verify(contextManager).runCompaction(eq(CompactTrigger.MANUAL), anyList());
    }
}
