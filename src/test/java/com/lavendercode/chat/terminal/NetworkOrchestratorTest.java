package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NetworkOrchestratorTest {

    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private SessionManager sessionManager;
    private LlmProvider provider;
    private ChatService chatService;
    private NetworkOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        inputQueue = new LinkedBlockingQueue<>();
        renderQueue = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        sessionManager = new InMemorySessionManager();
        provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");
        chatService = mock(ChatService.class);
        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai-compatible", "gpt-4", "http://localhost", "key"), null);

        orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "gpt-4", config, Theme.dark()
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        new Thread(() -> {
            try { inputQueue.put(new InputEvent.Shutdown()); } catch (InterruptedException e) {}
        }).start();
    }

    @Test
    void sendMessageShouldPutAddUserMessage() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.SendMessage("hello"));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddUserMessage.class);
        assertThat(((RenderEvent.AddUserMessage) event).text()).isEqualTo("hello");
    }

    @Test
    void clearCommandShouldPutClearChat() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, ""));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.ClearChat.class);
    }

    @Test
    void themeCommandShouldPutThemeChange() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.THEME, "light"));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.ThemeChange.class);
        assertThat(((RenderEvent.ThemeChange) event).theme().name()).isEqualTo("light");
    }

    @Test
    void exitCommandShouldPutShutdown() throws Exception {
        new Thread(orchestrator::run).start();
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.Shutdown.class);
    }

    @Test
    void helpCommandShouldPutSystemMessage() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, ""));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddSystemMessage.class);
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/exit");
    }
}
