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

    private final InputAreaLayout inputLayout;
    private int viewportHeight;
    private int separatorTopRow;
    private int inputFirstRow;
    private int inputLastRow;
    private int separatorBotRow;

    public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                            Theme theme, String modelName, InputAreaLayout inputLayout) {
        this.terminal = terminal;
        this.renderQueue = renderQueue;
        this.blocks = new ArrayList<>();
        this.theme = theme;
        this.modelName = modelName != null ? modelName : "";
        this.inputLayout = inputLayout;
        recalcLayout();
    }

    private void recalcLayout() {
        recalcLayout(inputLayout.editRows());
    }

    private void recalcLayout(int editRows) {
        int h = terminal.getSize().getRows();
        inputLayout.update(h, editRows);
        separatorTopRow = inputLayout.separatorTopRow();
        inputFirstRow = inputLayout.inputFirstRow();
        inputLastRow = inputLayout.inputLastRow();
        separatorBotRow = inputLayout.separatorBotRow();
        viewportHeight = Math.max(1, separatorTopRow - STATUS_HEIGHT);
        clampViewport();
    }

    /** The renderer exposes the input row so InputSystem knows where to place the cursor. */
    public int inputRow() { return inputFirstRow; }

    public void run() {
        registerResizeHandler();
        terminal.puts(InfoCmp.Capability.enter_ca_mode);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();
        try {
            LavenderSplash.show(terminal);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drawFull();

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
            case RenderEvent.AddUserMessage(var text) -> {
                autoScroll = true;
                addBlock(Role.USER, text);
            }
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
            case RenderEvent.ScrollPageUp() -> scrollDelta(-viewportHeight);
            case RenderEvent.ScrollPageDown() -> scrollDelta(viewportHeight);
            case RenderEvent.ScrollAutoReset() -> {
                autoScroll = true;
                scrollToBottom();
                drawViewport();
            }
            case RenderEvent.WindowResize(int c, int r) -> {
                recalcLayout(inputLayout.editRows());
                reflowAll();
                drawFull();
            }
            case RenderEvent.StatusUpdate(var m, int tc, boolean __) -> {
                this.modelName = m;
                this.tokenCount = tc;
                drawStatusBar();
            }
            case RenderEvent.RefreshInputChrome(var done) -> {
                drawInputDraft("", 0);
                if (done != null) done.countDown();
            }
            case RenderEvent.UpdateInputDraft(var draft, int cursor, var done) -> {
                drawInputDraft(draft, cursor);
                if (done != null) done.countDown();
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
        drawInputDraft("", 0);
    }

    private void drawInputChrome() {
        drawSeparator(separatorTopRow);
        drawInputArea();
        drawSeparator(separatorBotRow);
    }

    private void drawInputDraft(String draft, int cursorIndex) {
        int width = terminal.getWidth();
        int terminalRows = terminal.getSize().getRows();
        int desiredRows = InputDraftLayout.desiredEditRows(draft, width, terminalRows);
        boolean layoutChanged = desiredRows != inputLayout.editRows();
        if (layoutChanged) {
            recalcLayout(desiredRows);
            drawViewport();
        }

        drawInputChrome();
        int editRows = inputLayout.editRows();
        var lines = InputDraftLayout.format(draft, cursorIndex, width, editRows);

        for (int i = 0; i < editRows; i++) {
            int row = inputFirstRow + i;
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().print(theme.apply(StyleCatalog.INPUT_BG, " ".repeat(width)).toAnsi(terminal));
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);

            if (i < lines.size()) {
                var line = lines.get(i);
                if (!line.prefix().isEmpty()) {
                    terminal.writer().print(theme.apply(StyleCatalog.PROMPT, line.prefix()).toAnsi(terminal));
                }
                String before = line.showCursor()
                    ? line.text().substring(0, line.cursorCol())
                    : line.text();
                String after = line.showCursor()
                    ? line.text().substring(line.cursorCol())
                    : "";
                if (!before.isEmpty()) {
                    terminal.writer().print(theme.apply(StyleCatalog.INPUT_TEXT, before).toAnsi(terminal));
                }
                if (line.showCursor()) {
                    terminal.writer().print(theme.apply(StyleCatalog.PROMPT, "\u2588").toAnsi(terminal));
                }
                if (!after.isEmpty()) {
                    terminal.writer().print(theme.apply(StyleCatalog.INPUT_TEXT, after).toAnsi(terminal));
                }
            }
        }
        terminal.flush();
    }

    private void drawSeparator(int row) {
        AttributedString sep = theme.apply(StyleCatalog.INPUT_BORDER,
            "\u2500".repeat(terminal.getWidth()));
        terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
        terminal.writer().print(sep.toAnsi(terminal));
        terminal.flush();
    }

    /** Draw input box background — prompt and text are handled by JLine3 readLine(). */
    private void drawInputArea() {
        int width = terminal.getWidth();
        AttributedString bg = theme.apply(StyleCatalog.INPUT_BG, " ".repeat(width));
        String bgAnsi = bg.toAnsi(terminal);
        for (int row = inputFirstRow; row <= inputLastRow; row++) {
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
            terminal.writer().print(bgAnsi);
        }
        terminal.flush();
    }

    private void drawStatusBar() {
        String status = String.format("Model: %s | Tokens: %d | Theme: %s",
            modelName, tokenCount, theme.name());
        AttributedString styled = theme.apply(StyleCatalog.STATUS_BAR,
            padRight(status, terminal.getWidth()));
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.puts(InfoCmp.Capability.clr_eol);
        terminal.writer().print(styled.toAnsi(terminal));
        terminal.flush();
    }

    private void drawViewport() {
        clampViewport();
        int totalLines = totalContentLines();
        int viewportEnd = separatorTopRow; // stop before separator

        for (int screenRow = STATUS_HEIGHT; screenRow < viewportEnd; screenRow++) {
            int contentIdx = viewportStart + (screenRow - STATUS_HEIGHT);
            terminal.puts(InfoCmp.Capability.cursor_address, screenRow, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);

            if (contentIdx < totalLines) {
                var lineInfo = getLineWithRole(contentIdx);
                if (lineInfo != null) {
                    // Draw role prefix on first line of each block
                    if (lineInfo.isFirstLine) {
                        drawRolePrefix(lineInfo.role);
                    } else {
                        drawContinuationPrefix(lineInfo.role);
                    }
                    // Draw message content
                    for (AttributedString seg : lineInfo.line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(screenRow, totalLines);
        }
        terminal.flush();
    }

    private void drawDiff(int startRow, int count) {
        int endRow = Math.min(startRow + count, separatorTopRow);
        int totalLines = totalContentLines();
        for (int row = startRow; row < endRow; row++) {
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            int contentIdx = viewportStart + (row - STATUS_HEIGHT);
            if (contentIdx >= 0 && contentIdx < totalLines) {
                var lineInfo = getLineWithRole(contentIdx);
                if (lineInfo != null) {
                    if (lineInfo.isFirstLine) {
                        drawRolePrefix(lineInfo.role);
                    } else {
                        drawContinuationPrefix(lineInfo.role);
                    }
                    for (AttributedString seg : lineInfo.line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(row, totalLines);
        }
        terminal.flush();
    }

    private void drawRolePrefix(Role role) {
        switch (role) {
            case USER ->
                terminal.writer().print(theme.apply(StyleCatalog.USER_MESSAGE, "You: ").toAnsi(terminal));
            case ASSISTANT ->
                terminal.writer().print(theme.apply(StyleCatalog.ASSISTANT_BORDER, "\u2502 ").toAnsi(terminal));
            case SYSTEM ->
                terminal.writer().print(theme.apply(StyleCatalog.SYSTEM_MESSAGE, "  ").toAnsi(terminal));
        }
    }

    private void drawContinuationPrefix(Role role) {
        switch (role) {
            case USER -> terminal.writer().print("     "); // 5 spaces = "You: "
            case ASSISTANT ->
                terminal.writer().print(theme.apply(StyleCatalog.ASSISTANT_BORDER, "\u2502 ").toAnsi(terminal));
            case SYSTEM -> terminal.writer().print("  ");
        }
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
        int aiWidth = Math.max(1, terminal.getWidth() - 3); // "│ " prefix(2) + scrollbar(1)
        currentAIBlock.append(text, aiWidth);
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
        int thinkWidth = Math.max(1, terminal.getWidth() - 5); // "│ " prefix(2) + indent(2) + scrollbar(1)
        currentAIBlock.appendThinking(text, thinkWidth);
        if (autoScroll) {
            scrollToBottom();
            drawViewport();
        }
    }

    private void addBlock(Role role, String text) {
        MessageBlock block = new MessageBlock(role);
        int contentWidth = switch (role) {
            case USER -> Math.max(1, terminal.getWidth() - 6); // "You: " prefix(5) + scrollbar(1)
            default -> Math.max(1, terminal.getWidth() - 3);   // prefix(2) + scrollbar(1)
        };
        block.append(text, contentWidth);
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
        }
        autoScroll = viewportStart >= maxViewportStart();
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

    private record LineWithRole(RenderedLine line, Role role, boolean isFirstLine) {}

    private LineWithRole getLineWithRole(int globalIndex) {
        int remaining = globalIndex;
        for (MessageBlock block : blocks) {
            List<RenderedLine> lines = block.allLines();
            if (remaining < lines.size()) {
                return new LineWithRole(lines.get(remaining), block.role(), remaining == 0);
            }
            remaining -= lines.size();
        }
        return null;
    }

    private void reflowAll() {
        int reflowWidth = Math.max(1, terminal.getWidth() - 3); // conservative: 2-char prefix + scrollbar
        blocks.forEach(b -> b.reflow(reflowWidth));
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
