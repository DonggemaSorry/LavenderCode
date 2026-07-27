package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRendererInputDraftTest {

    private Terminal terminal;
    private TerminalRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderer = new TerminalRenderer(
            terminal,
            new LinkedBlockingQueue<>(),
            Theme.dark(),
            "test",
            "model",
            new InputAreaLayout()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) {
            terminal.close();
        }
    }

    @Test
    void completionMenuRedrawPreservesCurrentDraft() {
        renderer.handle(new RenderEvent.UpdateInputDraft("hello", 5));

        renderer.handle(new RenderEvent.CompletionMenu(
            List.of(new RenderEvent.CompletionEntry("help", "show help")),
            0,
            true
        ));

        assertThat(renderer.currentDraft()).isEqualTo("hello");
        assertThat(renderer.currentCursorIndex()).isEqualTo(5);
    }

    @Test
    void fullRedrawPreservesCurrentDraft() {
        renderer.handle(new RenderEvent.UpdateInputDraft("still typing", 12));

        renderer.handle(new RenderEvent.RefreshAll());

        assertThat(renderer.currentDraft()).isEqualTo("still typing");
        assertThat(renderer.currentCursorIndex()).isEqualTo(12);
    }

    @Test
    void updateInputDraftCanClearDraft() {
        renderer.handle(new RenderEvent.UpdateInputDraft("hello", 5));
        renderer.handle(new RenderEvent.UpdateInputDraft("", 0));

        assertThat(renderer.currentDraft()).isEmpty();
        assertThat(renderer.currentCursorIndex()).isZero();
    }
}
