package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkOrchestrator {

    private final AtomicReference<RequestContext> currentRequest = new AtomicReference<>();
    private final ChatService chatService;
    private final DeltaBuffer deltaBuffer;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final BlockingQueue<InputEvent> inputQueue;
    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String providerName;
    private final String modelName;
    private final LlmConfig config;
    private final ScheduledExecutorService timerScheduler;
    private volatile ScheduledFuture<?> timerTask;
    private volatile ResponseTimer currentTimer;

    public NetworkOrchestrator(ChatService chatService, DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               ScheduledExecutorService timerScheduler) {
        this.chatService = chatService;
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.providerName = providerName;
        this.modelName = modelName;
        this.config = config;
        this.timerScheduler = timerScheduler;
    }

    public void run() {
        safePut(new RenderEvent.StatusUpdate(providerName, modelName, "", 0));
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ExecuteCommand cmd -> {
                        if (handleCommand(cmd)) return;
                    }
                    case InputEvent.Shutdown __ -> { handleShutdown(); return; }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSendMessage(InputEvent.SendMessage msg) {
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));
        sessionManager.addUserMessage(msg.text());

        var ctxRef = new AtomicReference<RequestContext>();
        final ResponseTimer timer = new ResponseTimer();
        timer.start();
        this.currentTimer = timer;
        final ScheduledFuture<?> ft = timerScheduler.scheduleAtFixedRate(() -> {
            safePut(new RenderEvent.StatusUpdate(
                providerName, modelName,
                "Imagining\u2026 (" + timer.elapsedSeconds() + "s)", 0));
        }, 1, 1, TimeUnit.SECONDS);
        this.timerTask = ft;
        try {
            ctxRef.set(chatService.submit(
                provider, sessionManager.getHistory(), config,
                delta -> onDeltaReceived(ctxRef.get(), delta)
            ));
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            if (ft != null) ft.cancel(false);
            currentRequest.set(null);
            deltaBuffer.forceFlush();
            safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
            safePut(new RenderEvent.FinalizeMessage());
        }
    }

    private boolean handleCommand(InputEvent.ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
                cancelTimer();
                RequestContext ctx = currentRequest.getAndSet(null);
                if (ctx != null) {
                    chatService.cancel(ctx);
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
            case CLEAR -> {
                deltaBuffer.forceFlush();
                sessionManager.clear();
                safePut(new RenderEvent.ClearChat());
            }
            case EXIT, QUIT -> { handleShutdown(); return true; }
            case HELP -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage("""
                    Commands:
                      /exit       - Exit LavenderCode
                      /clear      - Clear conversation history
                      /help       - Show this help
                    Keyboard:
                      ↑/↓         - Scroll one line
                      PageUp/Down - Scroll one page
                      Home/End    - Jump to top/bottom
                      Mouse wheel - Scroll message area
                      Ctrl+C      - Cancel current request
                      Ctrl+D (empty) - Exit
                      Enter       - Send message
                      Ctrl+J      - Insert newline"""));
            }
            case SCROLL -> {
                deltaBuffer.forceFlush();
                RenderEvent se = parseScrollEvent(cmd.args());
                if (se != null) safePut(se);
            }
        }
        return false;
    }

    private void handleShutdown() {
        cancelTimer();
        RequestContext ctx = currentRequest.getAndSet(null);
        if (ctx != null) chatService.cancel(ctx);
        deltaBuffer.clear();

        // Drain the render queue of all pending events so the renderer
        // does not waste time processing streaming deltas before Shutdown.
        drainRenderQueue();

        safePut(new RenderEvent.Shutdown());
    }

    /**
     * Drains all pending events from the render queue.
     * During shutdown, no historical events need to be preserved.
     */
    private void drainRenderQueue() {
        renderQueue.drainTo(new java.util.ArrayList<>());
    }

    private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
        if (currentRequest.get() != ctx) return;

        switch (delta) {
            case DeltaEvent.Content(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, t, 0));
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(providerName, modelName, null, i + o));
            case DeltaEvent.Complete() -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    deltaBuffer.forceFlush();
                    long seconds = currentTimer != null ? currentTimer.elapsedSeconds() : 0;
                    safePut(new RenderEvent.StatusUpdate(
                        providerName, modelName, "Done (" + seconds + "s)", 0));
                    safePut(new RenderEvent.FinalizeMessage());
                    // Clear status after 1s
                    timerScheduler.schedule(() -> safePut(
                        new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                        1, TimeUnit.SECONDS);
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
        }
    }

    private RenderEvent parseScrollEvent(String args) {
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

    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
        if (currentTimer != null) {
            currentTimer.stop();
            currentTimer = null;
        }
    }

    private void safePut(RenderEvent event) {
        try { renderQueue.put(event); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
