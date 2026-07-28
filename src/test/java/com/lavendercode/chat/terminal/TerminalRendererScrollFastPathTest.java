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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerminalRendererScrollFastPathTest {

    private Terminal terminal;
    private TerminalRenderer renderer;
    private ScrollRegionPainter mockPainter;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderer = new TerminalRenderer(
            terminal, new LinkedBlockingQueue<>(), Theme.dark(), "test", "model", new InputAreaLayout());
        mockPainter = mock(ScrollRegionPainter.class);
        when(mockPainter.available()).thenReturn(true);
        renderer.setScrollPainter(mockPainter);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    /** 灌入足够文本使内容远超一页（视口高约 20 行，灌 60+ 行）。 */
    private void fillBeyondOnePage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("history line ").append(i).append('\n');
        }
        renderer.handle(new RenderEvent.AppendToMessage(sb.toString()));
    }

    @Test
    void scrollAppendShouldUseScrollUpWhenCapable() {
        fillBeyondOnePage();

        renderer.handle(new RenderEvent.AppendToMessage("new line A\nnew line B\n"));

        verify(mockPainter).scrollUp(anyInt(), anyInt(), intThat(s -> s > 0));
    }

    @Test
    void scrollAppendShouldDrawOnlyBottomRows() {
        fillBeyondOnePage();
        long baseline = renderer.drawnRowCount();

        renderer.handle(new RenderEvent.AppendToMessage("only one more line\n"));

        // 快路径绘制行数 ≈ scrolled+1（远小于整个视口高度）
        assertThat(renderer.drawnRowCount() - baseline).isLessThan(10);
    }

    @Test
    void shouldFallBackToFullViewportWhenNotAvailable() {
        renderer.setScrollPainter(new ScrollRegionPainter(terminal)); // DumbTerminal：available=false
        fillBeyondOnePage();
        long baseline = renderer.drawnRowCount();

        renderer.handle(new RenderEvent.AppendToMessage("degraded line\n"));

        // 降级 drawViewport：重画整个视口
        assertThat(renderer.drawnRowCount() - baseline).isGreaterThan(10);
    }

    @Test
    void shouldFallBackWhenScrolledExceedsViewport() {
        fillBeyondOnePage();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            huge.append("burst line ").append(i).append('\n');
        }

        renderer.handle(new RenderEvent.AppendToMessage(huge.toString()));

        verify(mockPainter, never()).scrollUp(anyInt(), anyInt(), anyInt());
    }

    @Test
    void appendWhileScrolledUpShouldNotRedrawContentRows() {
        fillBeyondOnePage();
        renderer.handle(new RenderEvent.ScrollTo(0)); // 用户上翻到顶
        long baseline = renderer.drawnRowCount();

        renderer.handle(new RenderEvent.AppendToMessage("off-screen line\n"));

        // 新增行在屏外：不重画任何内容行，只增量更新滚动条 cell
        assertThat(renderer.drawnRowCount() - baseline).isZero();
        verify(mockPainter, never()).scrollUp(anyInt(), anyInt(), anyInt());
    }
}
