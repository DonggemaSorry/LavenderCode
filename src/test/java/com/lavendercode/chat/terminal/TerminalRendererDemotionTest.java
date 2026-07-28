package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRendererDemotionTest {

    private Terminal terminal;
    private TerminalRenderer renderer;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        renderer = new TerminalRenderer(
            terminal, new LinkedBlockingQueue<>(), Theme.dark(), "test", "model", new InputAreaLayout());
        renderer.handle(new RenderEvent.AppendToMessage("some answer text\n"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    private void assertDemotedToViewport(RenderEvent event) {
        long fullBaseline = renderer.fullDrawCount();
        long rowsBaseline = renderer.drawnRowCount();

        renderer.handle(event);

        assertThat(renderer.fullDrawCount()).isEqualTo(fullBaseline); // 不再 clear_screen
        assertThat(renderer.drawnRowCount()).isGreaterThan(rowsBaseline); // 但确实重画了视口
    }

    @Test
    void toolCallRenderShouldNotTriggerFullDraw() {
        assertDemotedToViewport(new RenderEvent.ToolCallRender("id1", "Bash", Map.of("command", "ls"), "running"));
    }

    @Test
    void toolResultRenderShouldNotTriggerFullDraw() {
        renderer.handle(new RenderEvent.ToolCallRender("id1", "Bash", Map.of("command", "ls"), "running"));
        assertDemotedToViewport(new RenderEvent.ToolResultRender("id1", "ok", true, 128));
    }

    @Test
    void finalizeMessageShouldNotTriggerFullDraw() {
        assertDemotedToViewport(new RenderEvent.FinalizeMessage());
    }

    @Test
    void permissionPromptDismissShouldNotTriggerFullDraw() {
        assertDemotedToViewport(new RenderEvent.PermissionPromptDismiss());
    }

    @Test
    void clearChatShouldStillTriggerFullDraw() {
        long baseline = renderer.fullDrawCount();

        renderer.handle(new RenderEvent.ClearChat());

        assertThat(renderer.fullDrawCount()).isEqualTo(baseline + 1);
    }

    @Test
    void windowResizeShouldStillTriggerFullDraw() {
        long baseline = renderer.fullDrawCount();

        renderer.handle(new RenderEvent.WindowResize(100, 30));

        assertThat(renderer.fullDrawCount()).isEqualTo(baseline + 1);
    }
}
