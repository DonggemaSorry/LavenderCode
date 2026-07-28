package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRendererFlatCacheTest {

    private Terminal terminal;
    private TerminalRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderer = new TerminalRenderer(
            terminal, new LinkedBlockingQueue<>(), Theme.dark(), "test", "model", new InputAreaLayout());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    private List<String> currentCacheTexts() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < renderer.debugTotalLines(); i++) {
            out.add(renderer.debugLineText(i));
        }
        return out;
    }

    @Test
    void appendShouldNotTriggerFullRebuild() {
        renderer.handle(new RenderEvent.AppendToMessage("first line\n"));
        renderer.debugRebuildFlatCache();
        int baseline = renderer.rebuildCount();

        renderer.handle(new RenderEvent.AppendToMessage("second line\n"));
        renderer.handle(new RenderEvent.AppendToMessage("third line\n"));

        assertThat(renderer.rebuildCount()).isEqualTo(baseline);
    }

    @Test
    void incrementalCacheShouldMatchFullRebuild() {
        renderer.handle(new RenderEvent.AppendToMessage("alpha\nbeta\n"));
        renderer.handle(new RenderEvent.AddUserMessage("user question"));
        renderer.handle(new RenderEvent.AppendToMessage("gamma\n"));
        renderer.handle(new RenderEvent.AppendToMessage("delta"));
        List<String> incremental = currentCacheTexts();

        renderer.debugRebuildFlatCache();
        List<String> rebuilt = currentCacheTexts();

        assertThat(incremental).isEqualTo(rebuilt);
        assertThat(incremental).hasSizeGreaterThanOrEqualTo(5);
    }
}
