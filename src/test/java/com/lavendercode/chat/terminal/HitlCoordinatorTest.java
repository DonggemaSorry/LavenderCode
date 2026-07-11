package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.HitlChoice;
import com.lavendercode.core.permission.HitlRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class HitlCoordinatorTest {
    @Test
    void awaitDecisionCompletesWhenChoiceSubmitted() throws Exception {
        var queue = new LinkedBlockingQueue<RenderEvent>();
        var coord = new HitlCoordinator(queue);
        var req = new HitlRequest("Write", "src/a.java", "default 模式需确认", 0);
        var future = CompletableFuture.supplyAsync(() ->
            coord.awaitDecision(req, new AtomicBoolean(false)));
        Thread.sleep(100);
        var prompt = (RenderEvent.PermissionPrompt) queue.poll(1, TimeUnit.SECONDS);
        assertThat(prompt).isNotNull();
        coord.complete(HitlChoice.ALLOW_ONCE);
        assertThat(future.get(2, TimeUnit.SECONDS)).isEqualTo(HitlChoice.ALLOW_ONCE);
    }
}
