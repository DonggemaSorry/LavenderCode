package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.command.*;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NetworkOrchestratorCommandTest {

    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private SessionManager sessionManager;
    private LlmProvider provider;
    private LlmConfig config;
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
        config = new LlmConfig(
            List.of(ProviderConfig.of("openai-compatible", "openai-compatible", "gpt-4", "http://localhost", "key", null)),
            null);
        orchestrator = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot);
        var defs = BuiltinCommandRegistrar.builtinCommands();
        var registry = new CommandRegistry(defs);
        BuiltinCommandRegistrar.bindRegistry(registry);
        var ctx = new CommandContextImpl(orchestrator, null, null);
        orchestrator.bindCommandSystem(registry, ctx);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        try { inputQueue.put(new InputEvent.Shutdown()); } catch (InterruptedException e) {}
    }

    private RenderEvent pollUntil(Class<? extends RenderEvent> type) throws InterruptedException {
        RenderEvent event;
        while ((event = renderQueue.poll(2, TimeUnit.SECONDS)) != null) {
            if (type.isInstance(event)) return event;
        }
        return null;
    }

    @Test
    void statusCommandOutputsSixFields() throws Exception {
        var ctx = new CommandContextImpl(orchestrator, null, null);
        var registry = new CommandRegistry(BuiltinCommandRegistrar.builtinCommands());
        var statusDef = registry.find("status").orElseThrow();
        statusDef.handler().execute(ctx);
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        var text = ((RenderEvent.AddSystemMessage) event).text();
        assertThat(text).contains("Mode:", "Input tokens:", "Output tokens:", "Tools:", "Memories:", "Model:", "Directory:");
    }

    @Test
    void permissionCommandOutputsModeLabel() throws Exception {
        var ctx = new CommandContextImpl(orchestrator, null, null);
        var registry = new CommandRegistry(BuiltinCommandRegistrar.builtinCommands());
        var permDef = registry.find("permission").orElseThrow();
        permDef.handler().execute(ctx);
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
    }

    @Test
    void sessionCommandOutputsSessionInfo() throws Exception {
        var ctx = new CommandContextImpl(orchestrator, null, null);
        var registry = new CommandRegistry(BuiltinCommandRegistrar.builtinCommands());
        var sessionDef = registry.find("session").orElseThrow();
        sessionDef.handler().execute(ctx);
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("Session ID:");
    }

    @Test
    void unknownCommandShowsHelpHint() throws Exception {
        new Thread(orchestrator::run).start();
        inputQueue.put(new InputEvent.ExecuteCommand("/foobar"));
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("未知命令");
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/help");
    }

    @Test
    void helpCommandOutputsCommandList() throws Exception {
        new Thread(orchestrator::run).start();
        inputQueue.put(new InputEvent.ExecuteCommand("/help"));
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/exit");
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/status");
    }

    @Test
    void caseInsensitiveCommandMatch() throws Exception {
        new Thread(orchestrator::run).start();
        inputQueue.put(new InputEvent.ExecuteCommand("/Help"));
        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/exit");
    }
}