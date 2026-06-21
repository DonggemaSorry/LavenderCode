package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import java.util.concurrent.BlockingQueue;
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
    private final String modelName;
    private final LlmConfig config;
    private Theme theme;

    public NetworkOrchestrator(ChatService chatService, DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String modelName, LlmConfig config, Theme theme) {
        this.chatService = chatService;
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
    }

    public void run() {
        safePut(new RenderEvent.StatusUpdate(modelName, 0, false));
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ExecuteCommand cmd -> handleCommand(cmd);
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
        try {
            ctxRef.set(chatService.submit(
                provider, sessionManager.getHistory(), config,
                delta -> onDeltaReceived(ctxRef.get(), delta)
            ));
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            currentRequest.set(null);
            deltaBuffer.forceFlush();
            safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
            safePut(new RenderEvent.FinalizeMessage());
        }
    }

    private void handleCommand(InputEvent.ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
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
            case THEME -> {
                deltaBuffer.forceFlush();
                Theme newTheme = resolveTheme(cmd.args().trim().toLowerCase());
                if (newTheme != null) {
                    this.theme = newTheme;
                    safePut(new RenderEvent.ThemeChange(newTheme));
                }
            }
            case EXIT, QUIT -> handleShutdown();
            case HELP -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage("""
                    Commands:
                      /exit       - Exit LavenderCode
                      /clear      - Clear conversation history
                      /help       - Show this help
                      /theme dark - Switch to dark theme
                      /theme light- Switch to light theme
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
    }

    private void handleShutdown() {
        RequestContext ctx = currentRequest.getAndSet(null);
        if (ctx != null) chatService.cancel(ctx);
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.Shutdown());
    }

    private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
        if (currentRequest.get() != ctx) return;

        switch (delta) {
            case DeltaEvent.Content(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, t, 0));
            case DeltaEvent.Thinking(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.THINK_DELTA, t, 0));
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(modelName, i + o, false));
            case DeltaEvent.Complete() -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
        }
    }

    private Theme resolveTheme(String name) {
        return switch (name) {
            case "dark" -> Theme.dark();
            case "light" -> Theme.light();
            default -> null;
        };
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

    private void safePut(RenderEvent event) {
        try { renderQueue.put(event); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
