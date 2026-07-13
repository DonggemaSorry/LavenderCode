package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.memory.MemoryService;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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
    private NetworkOrchestrator orchestrator;
    private LlmConfig config;

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
            List.of(ProviderConfig.of("openai-compatible", "openai-compatible", "gpt-4", "http://localhost", "key", null)), null);

        orchestrator = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        new Thread(() -> {
            try { inputQueue.put(new InputEvent.Shutdown()); } catch (InterruptedException e) {}
        }).start();
    }

    private RenderEvent pollUntil(Class<? extends RenderEvent> type) throws InterruptedException {
        RenderEvent event;
        while ((event = renderQueue.poll(2, TimeUnit.SECONDS)) != null) {
            if (type.isInstance(event)) return event;
        }
        return null;
    }

    @Test
    void sendMessageShouldPutAddUserMessage() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.SendMessage("hello"));

        RenderEvent event = pollUntil(RenderEvent.AddUserMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddUserMessage) event).text()).isEqualTo("hello");
    }

    @Test
    void clearCommandShouldPutClearChat() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, ""));

        RenderEvent event = pollUntil(RenderEvent.ClearChat.class);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.ClearChat.class);
    }

    @Test
    void exitCommandShouldPutShutdown() throws Exception {
        new Thread(orchestrator::run).start();
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""));

        RenderEvent event = pollUntil(RenderEvent.Shutdown.class);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.Shutdown.class);
    }

    @Test
    void helpCommandShouldPutSystemMessage() throws Exception {
        new Thread(orchestrator::run).start();

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, ""));

        RenderEvent event = pollUntil(RenderEvent.AddSystemMessage.class);
        assertThat(event).isNotNull();
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/exit");
    }

    @Test
    void sendMessageInjectsFileInstructionsAndMemoryIntoSystemPrompt() throws Exception {
        when(provider.streamChat(any(), any(), any(), any())).thenReturn(completingIterator());
        NetworkOrchestrator withInstructions = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot,
            com.lavendercode.core.context.NoOpContextManager.INSTANCE,
            "File instructions.",
            () -> "Memory index."
        );
        Thread thread = new Thread(withInstructions::run);
        thread.start();

        inputQueue.put(new InputEvent.SendMessage("hello"));

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(PromptContext.class);
        verify(provider, timeout(2_000)).streamChat(any(), any(), any(), promptCaptor.capture());
        String stablePrompt = promptCaptor.getValue().stablePrompt();
        assertThat(stablePrompt).containsSubsequence(
            "Text Output",
            "File instructions.",
            "Memory index.");

        inputQueue.put(new InputEvent.Shutdown());
        thread.join(2_000);
    }

    @Test
    void completeEventTriggersMemoryUpdateWithLastUserMessage() throws Exception {
        when(provider.streamChat(any(), any(), any(), any())).thenReturn(completingIterator());
        RecordingMemoryService memoryService = new RecordingMemoryService(projectRoot);
        NetworkOrchestrator withMemory = new NetworkOrchestrator(
            deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "test-provider", "gpt-4", config,
            scheduler, projectRoot,
            com.lavendercode.core.context.NoOpContextManager.INSTANCE,
            "File instructions.",
            memoryService
        );
        Thread thread = new Thread(withMemory::run);
        thread.start();

        inputQueue.put(new InputEvent.SendMessage("remember the build command"));

        assertThat(memoryService.called.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(memoryService.turnCount).isEqualTo(1);
        assertThat(memoryService.lastUserMessage).isEqualTo("remember the build command");
        assertThat(memoryService.history).isNotEmpty();

        inputQueue.put(new InputEvent.Shutdown());
        thread.join(2_000);
    }

    private static StreamEventIterator completingIterator() {
        return new StreamEventIterator() {
            private boolean emitted;

            @Override
            public boolean hasNext() {
                return !emitted;
            }

            @Override
            public StreamEvent next() {
                emitted = true;
                return new StreamEvent.StreamComplete();
            }

            @Override
            public void close() {
            }
        };
    }

    private static final class RecordingMemoryService extends MemoryService {
        private final CountDownLatch called = new CountDownLatch(1);
        private volatile int turnCount;
        private volatile String lastUserMessage;
        private volatile List<Message> history = List.of();

        private RecordingMemoryService(Path projectRoot) {
            super(projectRoot, projectRoot);
        }

        @Override
        public void maybeUpdateAsync(LlmProvider provider, LlmConfig config,
                                     List<Message> recentMessages,
                                     int turnCount,
                                     String lastUserMessage) {
            this.history = recentMessages;
            this.turnCount = turnCount;
            this.lastUserMessage = lastUserMessage;
            called.countDown();
        }
    }
}
