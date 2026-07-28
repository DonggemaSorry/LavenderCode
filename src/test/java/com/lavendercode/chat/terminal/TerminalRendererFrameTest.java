package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRendererFrameTest {

    private Terminal terminal;
    private LinkedBlockingQueue<RenderEvent> renderQueue;
    private TerminalRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderQueue = new LinkedBlockingQueue<>();
        renderer = new TerminalRenderer(
            terminal, renderQueue, Theme.dark(), "test", "model", new InputAreaLayout());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    @Test
    void handleShouldApplyAndPaintOncePerCall() {
        long baseline = renderer.paintFrameCount();

        renderer.handle(new RenderEvent.AppendToMessage("a\n"));
        renderer.handle(new RenderEvent.AppendToMessage("b\n"));
        renderer.handle(new RenderEvent.AppendToMessage("c\n"));

        assertThat(renderer.paintFrameCount()).isEqualTo(baseline + 3);
    }

    @Test
    void updateDraftLatchShouldBeReleasedAfterPaint() {
        var latch = new CountDownLatch(1);

        renderer.handle(new RenderEvent.UpdateInputDraft("hello", 5, latch));

        assertThat(latch.getCount()).isZero();
        assertThat(renderer.currentDraft()).isEqualTo("hello");
    }

    @Test
    void emptyDirtyFrameShouldStillReleaseLatch() {
        var latch = new CountDownLatch(1);

        renderer.paintFrame(); // 空帧：不得泄漏 latch
        renderer.handle(new RenderEvent.RefreshInputChrome(latch));

        assertThat(latch.getCount()).isZero();
    }
}
