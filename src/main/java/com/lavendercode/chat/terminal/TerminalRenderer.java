package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;
import org.jline.utils.Signals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class TerminalRenderer {

    private final Terminal terminal;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final List<MessageBlock> blocks;
    private MessageBlock currentAIBlock;
    private int viewportStart;
    private boolean autoScroll = true;
    private Theme theme;
    private String modelName = "";
    private int tokenCount = 0;

    private static final int STATUS_HEIGHT = 1;
    private int viewportHeight;

    public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                            Theme theme) {
        this.terminal = terminal;
        this.renderQueue = renderQueue;
        this.blocks = new ArrayList<>();
        this.theme = theme;
        this.viewportHeight = Math.max(1, terminal.getSize().getRows() - STATUS_HEIGHT - 1);
    }

    public void run() {
        registerResizeHandler();
        terminal.puts(InfoCmp.Capability.enter_ca_mode);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();

        try {
            while (true) {
                RenderEvent event = renderQueue.take();
                if (event instanceof RenderEvent.Shutdown) break;
                dispatch(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.puts(InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
        }
    }

    private void dispatch(RenderEvent event) {
        switch (event) {
            case RenderEvent.AppendToMessage(var text) -> appendToAIBlock(text);
            case RenderEvent.FinalizeMessage() -> {
                if (currentAIBlock != null) {
                    currentAIBlock.markComplete();
                    currentAIBlock = null;
                }
            }
            case RenderEvent.AddUserMessage(var text) -> addBlock(Role.USER, text);
            case RenderEvent.AddSystemMessage(var text) -> addBlock(Role.SYSTEM, text);
            case RenderEvent.ThinkDelta(var text) -> appendThinking(text);
            case RenderEvent.ClearChat() -> {
                blocks.clear();
                currentAIBlock = null;
                viewportStart = 0;
                tokenCount = 0;
                drawFull();
            }
            case RenderEvent.ScrollTo(int n) -> {
                viewportStart = clampValue(n);
                autoScroll = false;
                drawViewport();
            }
            case RenderEvent.ScrollDelta(int d) -> scrollDelta(d);
            case RenderEvent.ScrollAutoReset() -> {
                autoScroll = true;
                scrollToBottom();
                drawViewport();
            }
            case RenderEvent.WindowResize(int c, int r) -> {
                viewportHeight = r - STATUS_HEIGHT - 1;
                reflowAll();
                drawFull();
            }
            case RenderEvent.ThemeChange(var t) -> {
                this.theme = t;
                drawFull();
            }
            case RenderEvent.StatusUpdate(var m, int tc, boolean __) -> {
                this.modelName = m;
                this.tokenCount = tc;
                drawStatusBar();
            }
            case RenderEvent.RefreshAll() -> drawFull();
            case RenderEvent.Shutdown() -> { /* handled in run() */ }
        }
    }

    // ===== drawing =====

    private void drawFull() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        drawStatusBar();
        drawViewport();
    }

    private void drawStatusBar() {
        String status = String.format("Model: %s | Tokens: %d | Theme: %s",
            modelName, tokenCount, theme.name());
        AttributedString styled = theme.apply(StyleCatalog.STATUS_BAR,
            padRight(status, terminal.getWidth()));
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.writer().print(styled.toAnsi(terminal));
        terminal.flush();
    }

    private void drawViewport() {
        clampViewport();
        int totalLines = totalContentLines();

        for (int screenRow = STATUS_HEIGHT; screenRow < terminal.getHeight() - 1; screenRow++) {
            int contentIdx = viewportStart + (screenRow - STATUS_HEIGHT);
            terminal.puts(InfoCmp.Capability.cursor_address, screenRow, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);

            if (contentIdx < totalLines) {
                RenderedLine line = getRenderedLine(contentIdx);
                if (line != null) {
                    for (AttributedString seg : line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(screenRow, totalLines);
        }
        terminal.flush();
    }

    private void drawDiff(int startRow, int count) {
        int endRow = Math.min(startRow + count, terminal.getHeight() - 1);
        int totalLines = totalContentLines();
        for (int row = startRow; row < endRow; row++) {
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            int contentIdx = viewportStart + (row - STATUS_HEIGHT);
            if (contentIdx >= 0 && contentIdx < totalLines) {
                RenderedLine line = getRenderedLine(contentIdx);
                if (line != null) {
                    for (AttributedString seg : line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(row, totalLines);
        }
        terminal.flush();
    }

    private void drawScrollbarCell(int screenRow, int totalLines) {
        if (totalLines <= viewportHeight) return;
        int sbCol = terminal.getWidth() - 1;
        terminal.puts(InfoCmp.Capability.cursor_address, screenRow, sbCol);
        double ratio = (double) viewportStart / Math.max(1, totalLines - viewportHeight);
        int thumbRow = STATUS_HEIGHT + (int) (ratio * (viewportHeight - 1));
        if (screenRow == thumbRow) {
            terminal.writer().print(theme.apply(StyleCatalog.SCROLLBAR_THUMB, "\u2588").toAnsi(terminal));
        } else {
            terminal.writer().print(theme.apply(StyleCatalog.SCROLLBAR_TRACK, "\u2502").toAnsi(terminal));
        }
    }

    // ===== block management =====

    private void appendToAIBlock(String text) {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
        }
        int oldCount = currentAIBlock.lineCount();
        int width = Math.max(1, terminal.getWidth() - 2);
        currentAIBlock.append(text, width);
        int added = currentAIBlock.lineCount() - oldCount;
        if (added > 0) {
            int firstRow = STATUS_HEIGHT + (blockToGlobalRow(currentAIBlock) + oldCount - viewportStart);
            drawDiff(firstRow, Math.min(added + 1, viewportHeight));
        }
        if (autoScroll) {
            scrollToBottom();
            drawViewport();
        }
    }

    private void appendThinking(String text) {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
        }
        int width = Math.max(1, terminal.getWidth() - 4);
        currentAIBlock.appendThinking(text, width);
        if (autoScroll) {
            scrollToBottom();
            drawViewport();
        }
    }

    private void addBlock(Role role, String text) {
        MessageBlock block = new MessageBlock(role);
        int width = Math.max(1, terminal.getWidth() - 2);
        block.append(text, width);
        block.markComplete();
        blocks.add(block);
        if (autoScroll) scrollToBottom();
        drawViewport();
    }

    // ===== scrolling =====

    private void scrollDelta(int delta) {
        int oldStart = viewportStart;
        viewportStart += delta;
        clampViewport();
        if (viewportStart != oldStart) {
            drawViewport();
            if (viewportStart == maxViewportStart()) autoScroll = true;
        }
    }

    private void scrollToBottom() { viewportStart = maxViewportStart(); }

    private int clampValue(int n) { return Math.max(0, Math.min(n, maxViewportStart())); }

    private void clampViewport() { viewportStart = Math.max(0, Math.min(viewportStart, maxViewportStart())); }

    private int maxViewportStart() { return Math.max(0, totalContentLines() - viewportHeight); }

    // ===== helpers =====

    private int totalContentLines() { return blocks.stream().mapToInt(MessageBlock::lineCount).sum(); }

    private int blockToGlobalRow(MessageBlock block) {
        int row = 0;
        for (MessageBlock b : blocks) { if (b == block) return row; row += b.lineCount(); }
        return row;
    }

    private RenderedLine getRenderedLine(int globalIndex) {
        int remaining = globalIndex;
        for (MessageBlock block : blocks) {
            List<RenderedLine> lines = block.allLines();
            if (remaining < lines.size()) return lines.get(remaining);
            remaining -= lines.size();
        }
        return null;
    }

    private void reflowAll() {
        int width = Math.max(1, terminal.getWidth() - 2);
        blocks.forEach(b -> b.reflow(width));
        clampViewport();
    }

    private String padRight(String s, int width) {
        return width <= s.length() ? s : s + " ".repeat(width - s.length());
    }

    private void registerResizeHandler() {
        Signals.register("WINCH", () -> {
            try {
                renderQueue.put(new RenderEvent.WindowResize(
                    terminal.getWidth(), terminal.getHeight()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
