package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.HitlRequest;
import com.lavendercode.core.provider.Role;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import org.jline.utils.Signals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Simplified renderer for native-scroll mode.
 * Content is printed directly to the primary screen; the terminal handles scrolling.
 * No custom viewport, no scrollbar, no alternate screen buffer.
 */
public class TerminalRenderer {

    private final Terminal terminal;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final List<MessageBlock> blocks;
    private MessageBlock currentAIBlock;
    private MessageBlock pendingFinalize;
    private final StreamPrinter streamPrinter;
    private Theme theme;
    private String modeLabel = "";
    private String modelName = "";
    private String statusText = null;
    private int tokenCount = 0;
    private String currentToolName = "";
    private HitlRequest activePermissionPrompt;
    private int permissionPromptSelection;

    // Input state
    private String currentDraft = "";
    private int currentCursorIndex = 0;
    private int prevInputRows = 0;
    private int prevMenuRows = 0;
    /** True when the prompt was printed on its own line below a partial streaming line. */
    private boolean promptDetached;

    // Frame bookkeeping
    private final List<CountDownLatch> frameLatches = new ArrayList<>();
    private long paintFrameCount;
    private boolean contentDirty;
    private boolean inputDirty;
    private boolean permissionDirty;

    // Completion
    private List<RenderEvent.CompletionEntry> completionEntries = List.of();
    private int completionSelectedIndex = 0;
    private boolean completionVisible = false;

    /** Package-visible for tests — apply one event and paint one frame. */
    void handle(RenderEvent event) {
        apply(event);
        paintFrame();
    }

    /** Package-visible for tests — number of paintFrame calls since construction. */
    long paintFrameCount() { return paintFrameCount; }

    String currentDraft() { return currentDraft; }
    int currentCursorIndex() { return currentCursorIndex; }

    public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                            Theme theme, String providerName, String modelName,
                            InputAreaLayout inputLayout) {
        this.terminal = terminal;
        this.renderQueue = renderQueue;
        this.blocks = new ArrayList<>();
        this.theme = theme;
        this.modeLabel = providerName != null ? providerName : "";
        this.modelName = modelName != null ? modelName : "";
        this.streamPrinter = new StreamPrinter(terminal);
    }

    public void run() {
        registerResizeHandler();
        // No enter_ca_mode — use primary screen for native scrolling
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();
        try {
            LavenderSplash.show(terminal);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            while (true) {
                if (processBatch() < 0) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.flush();
        }
    }

    // ===== processBatch: frame coalescing =====

    int processBatch() throws InterruptedException {
        RenderEvent first = renderQueue.take();
        List<RenderEvent> batch = new ArrayList<>();
        batch.add(first);
        renderQueue.drainTo(batch);
        for (RenderEvent e : batch) {
            if (e instanceof RenderEvent.Shutdown) {
                paintFrame();
                return -1;
            }
            apply(e);
        }
        paintFrame();
        return batch.size();
    }

    // ===== apply: state changes + dirty flags =====

    private void apply(RenderEvent event) {
        switch (event) {
            case RenderEvent.AppendToMessage(var text) -> {
                ensureAIBlock();
                int aiWidth = Math.max(1, terminal.getWidth() - 3);
                currentAIBlock.append(text, aiWidth);
                contentDirty = true;
            }
            case RenderEvent.FinalizeMessage() -> {
                if (currentAIBlock != null) {
                    // 闭合末尾未完成行，让它走同一条逐行着色路径。
                    // 原生滚动下历史行不可重绘，不再做整体 Markdown 重渲染。
                    String rawText = currentAIBlock.getRawText();
                    int aiWidth = Math.max(1, terminal.getWidth() - 3);
                    if (!rawText.isEmpty() && !rawText.endsWith("\n")) {
                        currentAIBlock.append("\n", aiWidth);
                    }
                    currentAIBlock.closePendingTable(aiWidth); // 流结束即表格块闭合
                    currentAIBlock.markComplete();
                    pendingFinalize = currentAIBlock;
                    currentAIBlock = null;
                }
                contentDirty = true; // triggers finalize in paintFrame
                inputDirty = true;  // redraw prompt with updated token count
            }
            case RenderEvent.AddUserMessage(var text) -> {
                eraseInputDisplay();
                streamPrinter.printUserMessage(text);
                MessageBlock block = new MessageBlock(Role.USER);
                int w = Math.max(1, terminal.getWidth() - 6);
                block.append(text, w);
                block.markComplete();
                blocks.add(block);
                inputDirty = true;
            }
            case RenderEvent.AddSystemMessage(var text) -> {
                eraseInputDisplay();
                streamPrinter.printSystemMessage(text);
                MessageBlock block = new MessageBlock(Role.SYSTEM);
                int w = Math.max(1, terminal.getWidth() - 3);
                block.append(text, w);
                block.markComplete();
                blocks.add(block);
                inputDirty = true;
            }
            case RenderEvent.ClearChat() -> {
                blocks.clear();
                currentAIBlock = null;
                pendingFinalize = null;
                streamPrinter.reset();
                tokenCount = 0;
                prevInputRows = 0;
                prevMenuRows = 0;
                promptDetached = false;
                terminal.puts(InfoCmp.Capability.clear_screen);
                terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
                terminal.flush();
                inputDirty = true;
            }
            case RenderEvent.WindowResize(int c, int r) -> {
                // On primary screen, already-printed content stays as-is.
                // Only future output uses the new width.
                inputDirty = true;
            }
            case RenderEvent.StatusUpdate(var ml, var mn, var st, int tc) -> {
                this.modeLabel = ml;
                this.modelName = mn;
                if (st != null) this.statusText = st;
                this.tokenCount = tc;
                inputDirty = true;
            }
            case RenderEvent.PermissionPrompt(var req, var future) -> {
                activePermissionPrompt = req;
                permissionPromptSelection = req.selectedIndex();
                permissionDirty = true;
            }
            case RenderEvent.PermissionPromptDismiss() -> {
                activePermissionPrompt = null;
                permissionPromptSelection = 0;
                permissionDirty = true;
            }
            case RenderEvent.ToolCallRender(var tcid, var tname, var params, var status) -> {
                currentToolName = tname;
                ensureAIBlock();
                String paramsSummary = formatToolParams(tname, params);
                int tw = Math.max(1, terminal.getWidth() - 3);
                currentAIBlock.appendToolRow(tname, paramsSummary, status, null, true, tw);
                contentDirty = true;
            }
            case RenderEvent.ToolResultRender(var tcid, var summary, boolean ok, int len) -> {
                ensureAIBlock();
                int tw = Math.max(1, terminal.getWidth() - 3);
                currentAIBlock.appendToolRow(currentToolName, null, "done", summary, ok, tw);
                contentDirty = true;
            }
            case RenderEvent.RefreshInputChrome(var done) -> {
                inputDirty = true;
                if (done != null) frameLatches.add(done);
            }
            case RenderEvent.UpdateInputDraft(var draft, int cursor, var done) -> {
                currentDraft = draft != null ? draft : "";
                currentCursorIndex = Math.max(0, Math.min(cursor, currentDraft.length()));
                inputDirty = true;
                if (done != null) frameLatches.add(done);
            }
            case RenderEvent.CompletionMenu(var entries, int selected, boolean visible) -> {
                boolean changed = visible != completionVisible
                    || selected != completionSelectedIndex
                    || !entries.equals(completionEntries);
                completionEntries = entries;
                completionSelectedIndex = selected;
                completionVisible = visible;
                if (changed) inputDirty = true;
            }
            case RenderEvent.CompletionEntry(var name, var description) -> {
                // Completion entry rendering will be implemented in Task 12
            }
            case RenderEvent.Shutdown() -> { /* handled in run() */ }
        }
    }

    // ===== paintFrame =====

    void paintFrame() {
        paintFrameCount++;
        try {
            // Erase previous input display before content changes
            if (contentDirty || inputDirty) {
                eraseInputDisplay();
            }

            // Content: stream or finalize
            if (contentDirty) {
                if (pendingFinalize != null) {
                    streamPrinter.finalizeMessage(pendingFinalize);
                    pendingFinalize = null;
                }
                if (currentAIBlock != null) {
                    streamPrinter.appendIncremental(currentAIBlock);
                }
                contentDirty = false;
            }

            // Redraw input
            if (inputDirty) {
                // Never print the prompt on the same row as a partial streaming
                // line — detach onto a fresh line so both can be updated safely.
                if (streamPrinter.hasPartialLine()) {
                    terminal.writer().print("\n");
                    promptDetached = true;
                } else {
                    promptDetached = false;
                }
                drawCompletionMenu();
                drawInputPrompt();
                inputDirty = false;
            }

            // Permission prompt overlay
            if (permissionDirty && activePermissionPrompt != null) {
                drawPermissionPrompt();
                permissionDirty = false;
            }
        } finally {
            for (var latch : frameLatches) {
                latch.countDown();
            }
            frameLatches.clear();
        }
    }

    // ===== Input prompt (with integrated status) =====

    private void drawInputPrompt() {
        String prompt = buildPrompt();
        String display = prompt + currentDraft;

        int termWidth = terminal.getWidth();
        int displayLines = Math.max(1, computeDisplayLines(display, termWidth));

        // Print prompt + draft
        terminal.writer().print(display);

        // Show cursor
        terminal.puts(InfoCmp.Capability.cursor_visible);
        terminal.flush();
        terminal.puts(InfoCmp.Capability.cursor_invisible);

        prevInputRows = displayLines;
    }

    private void eraseInputDisplay() {
        int totalRows = prevInputRows + prevMenuRows;
        if (totalRows > 0) {
            for (int i = 0; i < totalRows; i++) {
                terminal.puts(InfoCmp.Capability.carriage_return);
                terminal.puts(InfoCmp.Capability.clr_eol);
                if (i < totalRows - 1) {
                    // Move up one line
                    terminal.puts(InfoCmp.Capability.cursor_up, 1);
                }
            }
            // After erasing, cursor is at the first row of the old input area.
            // New content will print from here.
            prevInputRows = 0;
            prevMenuRows = 0;
            if (promptDetached) {
                // The prompt lived on its own line below a partial streaming
                // line — move back up so StreamPrinter can continue that line.
                terminal.puts(InfoCmp.Capability.cursor_up, 1);
                promptDetached = false;
            }
        }
    }

    private String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        if (!modelName.isEmpty()) {
            sb.append(modelName);
        }
        if (tokenCount > 0) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(tokenCount).append(" tok");
        }
        if (!sb.isEmpty()) {
            sb.append(" > ");
        } else {
            sb.append("> ");
        }
        return sb.toString();
    }

    private static int computeDisplayLines(String text, int width) {
        if (text.isEmpty()) return 1;
        int lines = 0;
        int col = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            int w = MessageBlock.charDisplayWidth(cp);
            if (col + w > width && col > 0) {
                lines++;
                col = w;
            } else {
                col += w;
            }
            i += charCount;
        }
        if (col > 0) lines++;
        return Math.max(1, lines);
    }

    // ===== Completion menu =====

    private void drawCompletionMenu() {
        prevMenuRows = 0;
        if (!completionVisible || completionEntries.isEmpty()) return;
        int maxWidth = terminal.getWidth() - 4;
        int menuHeight = Math.min(8, completionEntries.size());
        for (int i = 0; i < menuHeight; i++) {
            if (i >= completionEntries.size()) break;
            var entry = completionEntries.get(i);
            boolean selected = i == completionSelectedIndex;
            String prefix = selected ? "❯ " : "  ";
            String line = prefix + "/" + entry.name() + "  " + entry.description();
            if (line.length() > maxWidth) line = line.substring(0, maxWidth);
            var style = selected ? StyleCatalog.PROMPT : StyleCatalog.ASSISTANT_MESSAGE;
            terminal.writer().print(theme.apply(style, line).toAnsi(terminal));
            terminal.writer().print("\n");
            prevMenuRows++;
        }
        terminal.flush();
    }

    // ===== Permission prompt =====

    private void drawPermissionPrompt() {
        if (activePermissionPrompt == null) return;
        int width = Math.max(20, terminal.getWidth() - 2);
        String[] options = {
            "1. 允许本次",
            "2. 永久放行（写入本地规则）",
            "3. 拒绝本次"
        };

        terminal.writer().print("\n");
        terminal.writer().print(theme.apply(StyleCatalog.SYSTEM_MESSAGE,
            "┌─ 权限确认 " + "─".repeat(Math.max(0, width - 12))).toAnsi(terminal));
        terminal.writer().print("\n");

        String[] lines = {
            "工具: " + activePermissionPrompt.toolName(),
            "参数: " + activePermissionPrompt.detail(),
            "原因: " + activePermissionPrompt.reason(),
            ""
        };
        for (String line : lines) {
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().print(theme.apply(StyleCatalog.ASSISTANT_MESSAGE,
                "│ " + truncate(line, width - 2)).toAnsi(terminal));
            terminal.writer().print("\n");
        }

        for (int i = 0; i < options.length; i++) {
            terminal.puts(InfoCmp.Capability.clr_eol);
            String prefix = i == permissionPromptSelection ? "❯ " : "  ";
            var style = i == permissionPromptSelection ? StyleCatalog.PROMPT : StyleCatalog.ASSISTANT_MESSAGE;
            terminal.writer().print(theme.apply(style,
                "│ " + prefix + options[i]).toAnsi(terminal));
            terminal.writer().print("\n");
        }

        terminal.writer().print(theme.apply(StyleCatalog.INPUT_BORDER,
            "└" + "─".repeat(width)).toAnsi(terminal));
        terminal.writer().print("\n");
        terminal.flush();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    // ===== Block management =====

    private void ensureAIBlock() {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
        }
    }

    private String formatToolParams(String toolName, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        return switch (toolName) {
            case "read_file", "write_file", "edit_file" -> {
                Object path = params.get("path");
                yield path != null ? path.toString() : "";
            }
            case "execute_command" -> {
                Object cmd = params.get("command");
                String s = cmd != null ? cmd.toString() : "";
                yield s.length() > 60 ? s.substring(0, 57) + "..." : s;
            }
            case "search_file", "search_content" -> {
                Object p = params.get("pattern");
                yield p != null ? p.toString() : "";
            }
            default -> "";
        };
    }

    // ===== Test observability (package-visible) =====

    int blockCount() { return blocks.size(); }

    long drawnRowCount() { return streamPrinter.printedLineCount(); }

    long fullDrawCount() { return 0; } // No full draws in native-scroll mode

    int rebuildCount() { return 0; } // Flat cache removed

    void debugRebuildFlatCache() { /* no-op */ }

    int debugTotalLines() {
        int total = 0;
        for (MessageBlock b : blocks) total += b.lineCount();
        return total;
    }

    String debugLineText(int globalIndex) {
        int offset = 0;
        for (MessageBlock b : blocks) {
            List<RenderedLine> lines = b.allLines();
            if (globalIndex < offset + lines.size()) {
                return lines.get(globalIndex - offset).segments().toString();
            }
            offset += lines.size();
        }
        return null;
    }

    // ===== Resize handler =====

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
