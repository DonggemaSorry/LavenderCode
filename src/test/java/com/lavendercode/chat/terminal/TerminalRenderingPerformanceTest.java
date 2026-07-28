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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AC1：5000 行历史 + 流式追加 100 次，视口绘制行总数上界 O(新增行数 + 100)。 */
class TerminalRenderingPerformanceTest {

    private Terminal terminal;
    private TerminalRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderer = new TerminalRenderer(
            terminal, new LinkedBlockingQueue<>(), Theme.dark(), "test", "model", new InputAreaLayout());
        ScrollRegionPainter capable = mock(ScrollRegionPainter.class);
        when(capable.available()).thenReturn(true);
        renderer.setScrollPainter(capable);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    @Test
    void streamingAppendsShouldDrawOnlyNewRows() {
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            history.append("history line ").append(i).append('\n');
        }
        renderer.handle(new RenderEvent.AppendToMessage(history.toString()));
        assertThat(renderer.debugTotalLines()).isGreaterThanOrEqualTo(5000);

        long baseline = renderer.drawnRowCount();
        for (int i = 0; i < 100; i++) {
            renderer.handle(new RenderEvent.AppendToMessage("stream delta " + i + "\n"));
        }
        long drawn = renderer.drawnRowCount() - baseline;

        // 优化前 ≈ 100 × 视口高（≈2000）；快路径每次只画 scrolled+1 ≈ 2 行，留 5 倍余量防脆性
        assertThat(drawn).isLessThan(1000);
    }
}
