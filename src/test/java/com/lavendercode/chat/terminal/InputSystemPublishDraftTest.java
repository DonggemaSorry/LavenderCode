package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class InputSystemPublishDraftTest {

    private Terminal terminal;
    private LinkedBlockingQueue<RenderEvent> renderQueue;
    private InputSystem inputSystem;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderQueue = new LinkedBlockingQueue<>();
        inputSystem = new InputSystem(
            terminal, new LinkedBlockingQueue<>(), renderQueue, new InputAreaLayout());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    @Test
    void publishDraftShouldOfferAsyncWithoutLatch() {
        inputSystem.publishDraft("abc", 3);

        RenderEvent event = renderQueue.poll();
        assertThat(event).isInstanceOf(RenderEvent.UpdateInputDraft.class);
        var draft = (RenderEvent.UpdateInputDraft) event;
        assertThat(draft.draft()).isEqualTo("abc");
        assertThat(draft.cursorIndex()).isEqualTo(3);
        assertThat(draft.done()).isNull(); // 无 latch：调用方不等待渲染
    }

    @Test
    void publishDraftShouldReturnImmediatelyWhenQueueUnconsumed() {
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            inputSystem.publishDraft("x".repeat(i + 1), i + 1);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 队列无人消费也不阻塞（同步路径每次最多等 500ms，100 次将远超 1s）
        assertThat(elapsedMs).isLessThan(1000);
        assertThat(renderQueue).hasSize(100);
    }

    @Test
    void publishDraftSyncShouldStillCarryLatch() throws Exception {
        var consumer = new Thread(() -> {
            try {
                RenderEvent e = renderQueue.take();
                if (e instanceof RenderEvent.UpdateInputDraft(var d, var c, var done) && done != null) {
                    done.countDown();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();

        long start = System.nanoTime();
        inputSystem.publishDraftSync("", 0);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        consumer.join(2000);

        assertThat(elapsedMs).isLessThan(400); // latch 被放行即返回，未走到 500ms 超时
    }
}
