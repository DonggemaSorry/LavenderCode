package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.HitlChoice;
import com.lavendercode.core.permission.HitlGate;
import com.lavendercode.core.permission.HitlRequest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HitlCoordinator implements HitlGate {
    private final BlockingQueue<RenderEvent> renderQueue;
    private final AtomicReference<CompletableFuture<HitlChoice>> pending = new AtomicReference<>();
    private volatile HitlRequest currentRequest;
    private volatile int selectedIndex;

    public HitlCoordinator(BlockingQueue<RenderEvent> renderQueue) {
        this.renderQueue = renderQueue;
    }

    public boolean isAwaiting() {
        CompletableFuture<HitlChoice> future = pending.get();
        return future != null && !future.isDone();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void navigateSelection(int delta) {
        if (!isAwaiting() || currentRequest == null) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(2, selectedIndex + delta));
        offerPrompt(currentRequest, pending.get());
    }

    @Override
    public HitlChoice awaitDecision(HitlRequest request, AtomicBoolean cancelFlag) {
        CompletableFuture<HitlChoice> future = new CompletableFuture<>();
        pending.set(future);
        currentRequest = request;
        selectedIndex = request.selectedIndex();
        offerPrompt(request, future);
        try {
            while (!future.isDone()) {
                if (cancelFlag.get()) {
                    future.complete(HitlChoice.DENY);
                    break;
                }
                Thread.sleep(50);
            }
            return future.getNow(HitlChoice.DENY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.complete(HitlChoice.DENY);
            return HitlChoice.DENY;
        } finally {
            pending.set(null);
            currentRequest = null;
            selectedIndex = 0;
            renderQueue.offer(new RenderEvent.PermissionPromptDismiss());
        }
    }

    public void complete(HitlChoice choice) {
        CompletableFuture<HitlChoice> future = pending.get();
        if (future != null) {
            future.complete(choice);
        }
    }

    private void offerPrompt(HitlRequest request, CompletableFuture<HitlChoice> future) {
        renderQueue.offer(new RenderEvent.PermissionPrompt(
            new HitlRequest(request.toolName(), request.detail(), request.reason(), selectedIndex),
            future));
    }
}
