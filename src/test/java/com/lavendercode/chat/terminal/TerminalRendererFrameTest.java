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

    @Test
    void finalizeMessageAfterStreamingShouldNotThrow() {
        renderer.handle(new RenderEvent.AppendToMessage("partial line without newline"));

        renderer.handle(new RenderEvent.FinalizeMessage());

        assertThat(renderer.blockCount()).isEqualTo(1);
    }

    @Test
    void finalizeMessageWithoutContentShouldNotThrow() {
        renderer.handle(new RenderEvent.FinalizeMessage());

        assertThat(renderer.blockCount()).isZero();
    }

    @Test
    void statusUpdateDuringStreamingShouldNotThrow() {
        renderer.handle(new RenderEvent.AppendToMessage("streaming partial"));
        renderer.handle(new RenderEvent.StatusUpdate("default", "model", null, 42));
        renderer.handle(new RenderEvent.AppendToMessage(" more text\n"));
        renderer.handle(new RenderEvent.FinalizeMessage());

        assertThat(renderer.blockCount()).isEqualTo(1);
    }

    @Test
    void streamingTableShouldFinalizeWithoutError() {
        // 逐块流式输入表格：行闭合后进入缓冲会使 block 行数回缩
        renderer.handle(new RenderEvent.AppendToMessage("| a | b |"));
        renderer.handle(new RenderEvent.AppendToMessage("\n| --- | --- |\n"));
        renderer.handle(new RenderEvent.AppendToMessage("| 1 | 2 |\n"));
        renderer.handle(new RenderEvent.FinalizeMessage());

        assertThat(renderer.blockCount()).isEqualTo(1);
    }
}
