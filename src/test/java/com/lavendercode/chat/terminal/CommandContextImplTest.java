package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.LlmProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommandContextImplTest {

    @TempDir
    Path projectRoot;

    @Test
    void currentModeLabelReturnsModeLabel() {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        assertThat(ctx.currentModeLabel()).isNotNull();
    }

    @Test
    void totalInputTokensReturnsZeroInitially() {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        assertThat(ctx.totalInputTokens()).isZero();
    }

    @Test
    void modelNameReturnsOrchestratorModelName() {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        assertThat(ctx.modelName()).isEqualTo("gpt-4");
    }

    @Test
    void workingDirectoryReturnsProjectRoot() {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        assertThat(ctx.workingDirectory()).isEqualTo(projectRoot);
    }

    @Test
    void printMessageSendsAddSystemMessageToRenderQueue() throws Exception {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        ctx.printMessage("test message");
        var event = orch.renderQueue.poll(1, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddSystemMessage.class);
        assertThat(((RenderEvent.AddSystemMessage) event).text()).isEqualTo("test message");
    }

    @Test
    void injectUserMessageSendsAddUserMessage() throws Exception {
        var orch = createOrchestrator();
        var ctx = new CommandContextImpl(orch, null, null);
        new Thread(orch::run).start();
        ctx.injectUserMessage("injected text");
        RenderEvent event = null;
        for (int i = 0; i < 20; i++) {
            event = orch.renderQueue.poll(500, TimeUnit.MILLISECONDS);
            if (event instanceof RenderEvent.AddUserMessage) break;
        }
        assertThat(event).isInstanceOf(RenderEvent.AddUserMessage.class);
        assertThat(((RenderEvent.AddUserMessage) event).text()).isEqualTo("injected text");
        orch.inputQueue.put(new InputEvent.Shutdown());
    }

    private NetworkOrchestrator createOrchestrator() {
        var inputQueue = new LinkedBlockingQueue<InputEvent>();
        var renderQueue = new LinkedBlockingQueue<RenderEvent>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        var sessionManager = new InMemorySessionManager();
        var provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");
        var config = new LlmConfig(
            List.of(ProviderConfig.of("openai-compatible", "openai-compatible", "gpt-4", "http://localhost", "key", null)),
            null);
        return new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot);
    }
}