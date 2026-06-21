package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test: InputEvent -> NetworkOrchestrator -> RenderEvent pipeline.
 * Uses JLine3 DumbTerminal (headless, no real TTY needed).
 */
class TerminalChatIntegrationTest {

    private Terminal terminal;
    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private NetworkOrchestrator orchestrator;
    private LlmProvider provider;
    private ChatService chatService;
    private SessionManager sessionManager;
    private Thread networkThread;

    /**
     * Returns a StreamEventIterator that blocks on hasNext() until
     * interrupted or closed. This prevents the background ioPool
     * thread from emitting spurious events before the test sequence
     * completes.
     */
    private static StreamEventIterator blockingIterator() {
        return new StreamEventIterator() {
            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public boolean hasNext() {
                while (!closed.get()) {
                    try { Thread.sleep(50); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        closed.set(true);
                    }
                }
                return false;
            }

            @Override
            public StreamEvent next() { return null; }

            @Override
            public void close() { closed.set(true); }
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        // Direct DumbTerminal creation avoids corrupting Surefire's std/in
        terminal = new DumbTerminal(
            new java.io.ByteArrayInputStream(new byte[0]),
            System.out
        );
        inputQueue = new LinkedBlockingQueue<>();
        renderQueue = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        sessionManager = new InMemorySessionManager();

        provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");
        when(provider.streamChat(any(), any())).thenAnswer(inv -> blockingIterator());

        chatService = new StreamingChatService();

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai-compatible", "gpt-4", "http://localhost", "key"), null);

        orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "gpt-4", config
        );

        networkThread = new Thread(orchestrator::run, "lavender-network-test");
        networkThread.start();
    }

    @AfterEach
    void tearDown() {
        chatService.shutdown();
        networkThread.interrupt();
        scheduler.shutdownNow();
    }

    private RenderEvent pollUntil(Class<? extends RenderEvent> type) throws InterruptedException {
        RenderEvent event;
        while ((event = renderQueue.poll(2, TimeUnit.SECONDS)) != null) {
            if (type.isInstance(event)) return event;
        }
        return null;
    }

    @Test
    void shouldRenderUserMessage() throws Exception {
        inputQueue.put(new InputEvent.SendMessage("hello"));
        inputQueue.put(new InputEvent.Shutdown());

        RenderEvent e1 = pollUntil(RenderEvent.AddUserMessage.class);
        assertThat(e1).isNotNull();
        assertThat(((RenderEvent.AddUserMessage) e1).text()).isEqualTo("hello");
    }

    @Test
    void clearCommandShouldEmitClearChat() throws Exception {
        inputQueue.put(new InputEvent.SendMessage("msg1"));
        pollUntil(RenderEvent.AddUserMessage.class);

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, ""));
        inputQueue.put(new InputEvent.Shutdown());

        RenderEvent event = pollUntil(RenderEvent.ClearChat.class);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.ClearChat.class);
    }

    @Test
    void exitCommandShouldEmitShutdown() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""));

        RenderEvent event = pollUntil(RenderEvent.Shutdown.class);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.Shutdown.class);

        networkThread.join(2_000);
        assertThat(networkThread.isAlive()).isFalse();
    }
}
