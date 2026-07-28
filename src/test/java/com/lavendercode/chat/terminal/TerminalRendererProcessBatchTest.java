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

class TerminalRendererProcessBatchTest {

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
    void burstShouldCoalesceIntoSinglePaint() throws Exception {
        for (int i = 0; i < 50; i++) {
            renderQueue.put(new RenderEvent.AppendToMessage("line " + i + "\n"));
        }

        int processed = renderer.processBatch();

        assertThat(processed).isEqualTo(50);
        assertThat(renderer.paintFrameCount()).isEqualTo(1);
        assertThat(renderer.debugTotalLines()).isEqualTo(50);
    }

    @Test
    void singleEventShouldPaintImmediately() throws Exception {
        renderQueue.put(new RenderEvent.AppendToMessage("only one\n"));

        int processed = renderer.processBatch();

        assertThat(processed).isEqualTo(1);
        assertThat(renderer.paintFrameCount()).isEqualTo(1);
    }

    @Test
    void shutdownShouldReturnMinusOneAfterPainting() throws Exception {
        renderQueue.put(new RenderEvent.AppendToMessage("before shutdown\n"));
        renderQueue.put(new RenderEvent.Shutdown());

        int processed = renderer.processBatch();

        assertThat(processed).isEqualTo(-1);
        assertThat(renderer.paintFrameCount()).isEqualTo(1);
        assertThat(renderer.debugTotalLines()).isEqualTo(1);
    }

    @Test
    void latchesShouldBeReleasedAtFrameEnd() throws Exception {
        var latch = new CountDownLatch(1);
        renderQueue.put(new RenderEvent.UpdateInputDraft("draft", 5, latch));
        for (int i = 0; i < 10; i++) {
            renderQueue.put(new RenderEvent.AppendToMessage("x" + i + "\n"));
        }

        renderer.processBatch();

        assertThat(latch.getCount()).isZero();
        assertThat(renderer.currentDraft()).isEqualTo("draft");
    }
}
