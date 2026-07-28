package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.HitlRequest;
import com.lavendercode.core.provider.Role;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;
import org.jline.utils.Signals;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class TerminalRenderer {

    private final Terminal terminal;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final List<MessageBlock> blocks;
    private MessageBlock currentAIBlock;
    private int viewportStart;
    private boolean autoScroll = true;
    private Theme theme;
    private String modeLabel = "";
    private String modelName = "";
    private String statusText = null;
    private int tokenCount = 0;
    private String currentToolName = "";
    private HitlRequest activePermissionPrompt;
    private int permissionPromptSelection;

    private static final int STATUS_HEIGHT = 1;

    private final InputAreaLayout inputLayout;
    private int viewportHeight;
    private int separatorTopRow;
    private int inputFirstRow;
    private int inputLastRow;
    private int separatorBotRow;

    private List<LineWithRole> flatLineCache;
    private int[] blockStartLines;
    private boolean flatCacheDirty = true;
    int rebuildCount; // 包级可见：测试用，统计全量重建次数

    private List<RenderEvent.CompletionEntry> completionEntries = List.of();
    private int completionSelectedIndex = 0;
    private boolean completionVisible = false;

    private String currentDraft = "";
    private int currentCursorIndex = 0;

    private final EnumSet<DirtyRegion> dirty = EnumSet.noneOf(DirtyRegion.class);
    private final List<CountDownLatch> frameLatches = new ArrayList<>();
    private long paintFrameCount;
    private ScrollRegionPainter scrollPainter;
    private ViewportHint viewportHint; // null = 全量视口重绘
    private long drawnRowCount;        // 包级可见语义见 drawnRowCount()

    /** Package-visible for tests — apply one event and paint one frame. */
    void handle(RenderEvent event) {
        apply(event);
        paintFrame();
    }

    /** Package-visible for tests — number of paintFrame calls since construction. */
    long paintFrameCount() { return paintFrameCount; }

    // ===== test observability (package-visible) =====

    void setScrollPainter(ScrollRegionPainter painter) {
        this.scrollPainter = painter;
    }

    long drawnRowCount() {
        return drawnRowCount;
    }

    String currentDraft() {
        return currentDraft;
    }

    int currentCursorIndex() {
        return currentCursorIndex;
    }

    public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                            Theme theme, String providerName, String modelName,
                            InputAreaLayout inputLayout) {
        this.terminal = terminal;
        this.renderQueue = renderQueue;
        this.blocks = new ArrayList<>();
        this.theme = theme;
        this.modeLabel = providerName != null ? providerName : "";
        this.modelName = modelName != null ? modelName : "";
        this.inputLayout = inputLayout;
        this.scrollPainter = new ScrollRegionPainter(terminal);
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
                if (processBatch() < 0) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.puts(InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
        }
    }

    // ===== apply: state changes + dirty marking (no drawing) =====

    /**
     * 帧合并主循环单元：take 阻塞取首个事件 → drainTo 排空队列 →
     * 整批 apply → 统一 paintFrame 一次。
     * 返回处理的事件数；批内遇到 Shutdown 时先绘制已应用的事件再返回 -1。
     */
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

    private void apply(RenderEvent event) {
        switch (event) {
            case RenderEvent.AppendToMessage(var text) -> appendToAIBlock(text);
            case RenderEvent.FinalizeMessage() -> {
                if (currentAIBlock != null) {
                    String rawText = currentAIBlock.getRawText();
                    int contentWidth = terminal.getWidth() - 4;
                    List<RenderedLine> styled = MarkdownRenderer.render(rawText, contentWidth);
                    currentAIBlock.replaceLines(styled);
                    currentAIBlock.markComplete();
                    currentAIBlock = null;
                    flatCacheDirty = true;
                    dirtyAllRegions(); // Task 7 降级为 VIEWPORT
                }
            }
            case RenderEvent.AddUserMessage(var text) -> {
                autoScroll = true;
                addBlock(Role.USER, text);
            }
            case RenderEvent.AddSystemMessage(var text) -> addBlock(Role.SYSTEM, text);
            case RenderEvent.ClearChat() -> {
                blocks.clear();
                currentAIBlock = null;
                viewportStart = 0;
                tokenCount = 0;
                flatCacheDirty = true;
                dirtyAllRegions();
            }
            case RenderEvent.ScrollTo(int n) -> {
                viewportStart = clampValue(n);
                autoScroll = false;
                dirtyViewportFull();
            }
            case RenderEvent.ScrollDelta(int d) -> scrollDelta(d);
            case RenderEvent.ScrollPageUp() -> scrollDelta(-viewportHeight);
            case RenderEvent.ScrollPageDown() -> scrollDelta(viewportHeight);
            case RenderEvent.ScrollAutoReset() -> {
                autoScroll = true;
                scrollToBottom();
                dirtyViewportFull();
            }
            case RenderEvent.WindowResize(int c, int r) -> {
                recalcLayout(inputLayout.editRows());
                reflowAll();
                dirtyAllRegions();
            }
            case RenderEvent.StatusUpdate(var ml, var mn, var st, int tc) -> {
                this.modeLabel = ml;
                this.modelName = mn;
                if (st != null) {
                    this.statusText = st;
                }
                this.tokenCount = tc;
                dirty.add(DirtyRegion.STATUS_BAR);
            }
            case RenderEvent.PermissionPrompt(var req, var future) -> {
                activePermissionPrompt = req;
                permissionPromptSelection = req.selectedIndex();
                dirty.add(DirtyRegion.PERMISSION_PROMPT);
            }
            case RenderEvent.PermissionPromptDismiss() -> {
                activePermissionPrompt = null;
                permissionPromptSelection = 0;
                dirtyAllRegions(); // Task 7 降级为 VIEWPORT
            }
            case RenderEvent.ToolCallRender(var tcid, var tname, var params, var status) -> {
                currentToolName = tname;
                ensureAIBlock();
                String paramsSummary = formatToolParams(tname, params);
                int tw = Math.max(1, terminal.getWidth() - 3);
                currentAIBlock.appendToolRow(tname, paramsSummary, status, null, true, tw);
                flatCacheDirty = true;
                dirtyAllRegions(); // Task 7 降级为 VIEWPORT
            }
            case RenderEvent.ToolResultRender(var tcid, var summary, boolean ok, int len) -> {
                ensureAIBlock();
                String toolName = currentToolName;
                int tw = Math.max(1, terminal.getWidth() - 3);
                currentAIBlock.appendToolRow(toolName, null, "done", summary, ok, tw);
                flatCacheDirty = true;
                dirtyAllRegions(); // Task 7 降级为 VIEWPORT
            }
            case RenderEvent.RefreshInputChrome(var done) -> {
                dirty.add(DirtyRegion.INPUT_AREA);
                if (done != null) frameLatches.add(done);
            }
            case RenderEvent.UpdateInputDraft(var draft, int cursor, var done) -> {
                currentDraft = draft != null ? draft : "";
                currentCursorIndex = Math.max(0, Math.min(cursor, currentDraft.length()));
                dirty.add(DirtyRegion.INPUT_AREA);
                if (done != null) frameLatches.add(done);
            }
            case RenderEvent.RefreshAll() -> dirtyAllRegions();
            case RenderEvent.CompletionMenu(var entries, int selected, boolean visible) -> {
                completionEntries = entries;
                completionSelectedIndex = selected;
                completionVisible = visible;
                dirtyViewportFull();
                dirty.add(DirtyRegion.COMPLETION_MENU);
            }
            case RenderEvent.CompletionEntry(var name, var description) -> {
                // Completion entry rendering will be implemented in Task 12
            }
            case RenderEvent.Shutdown() -> { /* handled in run() */ }
        }
    }

    // ===== paintFrame: unified drawing based on dirty regions =====

    void paintFrame() {
        paintFrameCount++;
        try {
            if (dirty.isEmpty()) {
                return;
            }
            if (dirty.size() == DirtyRegion.values().length) {
                drawFull();
            } else {
                if (dirty.contains(DirtyRegion.STATUS_BAR)) drawStatusBar();
                if (dirty.contains(DirtyRegion.VIEWPORT)) paintViewport();
                if (dirty.contains(DirtyRegion.PERMISSION_PROMPT) && activePermissionPrompt != null) {
                    drawPermissionPrompt();
                }
                if (dirty.contains(DirtyRegion.INPUT_AREA)) redrawCurrentInputDraft();
                if (dirty.contains(DirtyRegion.COMPLETION_MENU)) drawCompletionMenu();
            }
        } finally {
            dirty.clear();
            viewportHint = null;
            for (var latch : frameLatches) {
                latch.countDown();
            }
            frameLatches.clear();
        }
    }

    /** VIEWPORT 分派：有提示走快路径，无提示或能力缺失降级 drawViewport。 */
    private void paintViewport() {
        switch (viewportHint) {
            case ViewportHint.ScrollAppend(var scrolled, var firstDirty, var prevThumb)
                    when scrollPainter.available() -> paintViewportScrollAppend(scrolled, firstDirty, prevThumb);
            case ViewportHint.DiffRows(var row, var count, var prevThumb)
                    -> paintViewportDiffRows(row, count, prevThumb);
            case null, default -> drawViewport();
        }
    }

    /** 滚动快路径：scrollUp 上移像素，只画底部新增行 + 滚动条 thumb 增量。 */
    private void paintViewportScrollAppend(int scrolled, int firstDirty, int prevThumb) {
        int firstScreenRow = STATUS_HEIGHT + (firstDirty - viewportStart);
        if (scrolled >= viewportHeight || firstScreenRow < STATUS_HEIGHT || firstScreenRow >= separatorTopRow) {
            drawViewport(); // F14 防御：越界降级，宁可重画不可画错
            return;
        }
        int total = totalContentLines();
        if (scrolled > 0) {
            scrollPainter.scrollUp(STATUS_HEIGHT, separatorTopRow - 1, scrolled);
            int residue = prevThumb - scrolled; // 旧 thumb 被硬件滚动后的残留位置
            if (residue >= STATUS_HEIGHT && residue < separatorTopRow) {
                drawScrollbarCell(residue, total);
            }
        }
        drawDiff(firstScreenRow, separatorTopRow - firstScreenRow);
        int thumb = scrollbarThumbRow(total);
        if (thumb >= STATUS_HEIGHT && thumb < separatorTopRow) {
            drawScrollbarCell(thumb, total);
        }
    }

    /** 局部行快路径：clamp 到视口画受影响行（屏外自动跳过），再增量更新滚动条 thumb。 */
    private void paintViewportDiffRows(int contentRow, int count, int prevThumb) {
        int firstScreenRow = STATUS_HEIGHT + (contentRow - viewportStart);
        int total = totalContentLines();
        if (firstScreenRow < separatorTopRow) {
            int from = Math.max(firstScreenRow, STATUS_HEIGHT);
            drawDiff(from, Math.min(count, separatorTopRow - from));
        }
        if (prevThumb >= STATUS_HEIGHT && prevThumb < separatorTopRow) {
            drawScrollbarCell(prevThumb, total);
        }
        int thumb = scrollbarThumbRow(total);
        if (thumb >= STATUS_HEIGHT && thumb < separatorTopRow) {
            drawScrollbarCell(thumb, total);
        }
    }

    private void dirtyAllRegions() {
        dirty.addAll(EnumSet.allOf(DirtyRegion.class));
    }

    private void dirtyViewportFull() {
        dirty.add(DirtyRegion.VIEWPORT);
        viewportHint = null;
    }

    /** autoScroll 末尾追加置脏：与既有 ScrollAppend 合并，与其他变更混叠则降级全量。 */
    private void markViewportScrollAppend(int scrolled, int firstDirty, int prevThumb) {
        if (!dirty.contains(DirtyRegion.VIEWPORT)) {
            dirty.add(DirtyRegion.VIEWPORT);
            viewportHint = new ViewportHint.ScrollAppend(scrolled, firstDirty, prevThumb);
        } else if (viewportHint instanceof ViewportHint.ScrollAppend(var s, var f, var p)) {
            viewportHint = new ViewportHint.ScrollAppend(s + scrolled, Math.min(f, firstDirty), p);
        } else {
            viewportHint = null; // 与全量/DiffRows 混叠 → 保守全量
        }
    }

    /** 局部行变化置脏（上翻中追加）：与既有 DiffRows 合并行范围，混叠则降级全量。 */
    private void markViewportDiffRows(int contentRow, int count, int prevThumb) {
        if (!dirty.contains(DirtyRegion.VIEWPORT)) {
            dirty.add(DirtyRegion.VIEWPORT);
            viewportHint = new ViewportHint.DiffRows(contentRow, count, prevThumb);
        } else if (viewportHint instanceof ViewportHint.DiffRows(var r, var c, var p)) {
            int from = Math.min(r, contentRow);
            int to = Math.max(r + c, contentRow + count);
            viewportHint = new ViewportHint.DiffRows(from, to - from, p);
        } else {
            viewportHint = null;
        }
    }

    // ===== drawing =====

    private void drawFull() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        drawStatusBar();
        drawViewport();
        if (activePermissionPrompt != null) {
            drawPermissionPrompt();
        }
        redrawCurrentInputDraft();
        drawCompletionMenu();
    }

    private void redrawCurrentInputDraft() {
        drawInputDraft(currentDraft, currentCursorIndex);
    }

    private void drawPermissionPrompt() {
        if (activePermissionPrompt == null) {
            return;
        }
        int width = Math.max(20, terminal.getWidth() - 2);
        String[] options = {
            "1. 允许本次",
            "2. 永久放行（写入本地规则）",
            "3. 拒绝本次"
        };
        int boxHeight = 8;
        int startRow = Math.max(STATUS_HEIGHT + 1, separatorTopRow - boxHeight - 1);

        terminal.puts(InfoCmp.Capability.cursor_address, startRow, 1);
        terminal.writer().print(theme.apply(StyleCatalog.SYSTEM_MESSAGE,
            "\u250C\u2500 权限确认 " + "\u2500".repeat(Math.max(0, width - 12))).toAnsi(terminal));

        String[] lines = {
            "工具: " + activePermissionPrompt.toolName(),
            "参数: " + activePermissionPrompt.detail(),
            "原因: " + activePermissionPrompt.reason(),
            ""
        };
        for (int i = 0; i < lines.length; i++) {
            terminal.puts(InfoCmp.Capability.cursor_address, startRow + 1 + i, 1);
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().print(theme.apply(StyleCatalog.ASSISTANT_MESSAGE,
                "\u2502 " + truncate(lines[i], width - 2)).toAnsi(terminal));
        }

        for (int i = 0; i < options.length; i++) {
            terminal.puts(InfoCmp.Capability.cursor_address, startRow + 5 + i, 1);
            terminal.puts(InfoCmp.Capability.clr_eol);
            String prefix = i == permissionPromptSelection ? "\u276F " : "  ";
            var style = i == permissionPromptSelection ? StyleCatalog.PROMPT : StyleCatalog.ASSISTANT_MESSAGE;
            terminal.writer().print(theme.apply(style,
                "\u2502 " + prefix + options[i]).toAnsi(terminal));
        }

        terminal.puts(InfoCmp.Capability.cursor_address, startRow + boxHeight - 1, 1);
        terminal.writer().print(theme.apply(StyleCatalog.INPUT_BORDER,
            "\u2514" + "\u2500".repeat(width)).toAnsi(terminal));
        terminal.flush();
    }

    private void drawCompletionMenu() {
        if (!completionVisible || completionEntries.isEmpty()) return;
        int maxWidth = terminal.getWidth() - 4;
        int menuHeight = Math.min(8, completionEntries.size());
        int startRow = separatorTopRow - menuHeight;
        for (int i = 0; i < menuHeight; i++) {
            int entryIdx = i;
            if (entryIdx >= completionEntries.size()) break;
            var entry = completionEntries.get(entryIdx);
            boolean selected = entryIdx == completionSelectedIndex;
            terminal.puts(InfoCmp.Capability.cursor_address, startRow + i, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            String prefix = selected ? "\u276F " : "  ";
            String line = prefix + "/" + entry.name() + "  " + entry.description();
            if (line.length() > maxWidth) line = line.substring(0, maxWidth);
            var style = selected ? StyleCatalog.PROMPT : StyleCatalog.ASSISTANT_MESSAGE;
            terminal.writer().print(theme.apply(style, line).toAnsi(terminal));
        }
        terminal.flush();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, Math.max(0, maxLen - 3)) + "...";
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
        int w = terminal.getWidth();
        int colW = w / 3;

        String left = modeLabel.isEmpty() ? modelName : "\u26E8 " + modeLabel;
        if (left.length() > colW - 1) {
            left = left.substring(0, colW - 1);
        }

        String mid = statusText != null ? statusText : "";
        if (mid.length() > colW - 2) mid = mid.substring(0, colW - 2);

        String right = modelName;
        if (tokenCount > 0) {
            right = modelName + " \u00b7 " + tokenCount + " tok";
        }
        if (right.length() > colW - 1) right = right.substring(0, colW - 1);

        // Center mid within its column
        int midTotalPad = Math.max(0, colW - mid.length());
        int midLeftPad = midTotalPad / 2;

        StringBuilder bar = new StringBuilder();
        bar.append(padRight(left, colW));
        bar.append("|");
        bar.append(" ".repeat(midLeftPad));
        bar.append(mid);
        bar.append(" ".repeat(midTotalPad - midLeftPad));
        bar.append("|");
        // Right-aligned model name
        bar.append(" ".repeat(Math.max(0, colW - right.length())));
        bar.append(right);

        AttributedString styled = theme.apply(StyleCatalog.STATUS_BAR, bar.toString());
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
            drawnRowCount++;
        }
        terminal.flush();
        drawCompletionMenu();
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
            drawnRowCount++;
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

    private int scrollbarThumbRow(int totalLines) {
        double ratio = (double) viewportStart / Math.max(1, totalLines - viewportHeight);
        return STATUS_HEIGHT + (int) (ratio * (viewportHeight - 1));
    }

    private void drawScrollbarCell(int screenRow, int totalLines) {
        if (totalLines <= viewportHeight) return;
        int sbCol = terminal.getWidth() - 1;
        terminal.puts(InfoCmp.Capability.cursor_address, screenRow, sbCol);
        int thumbRow = scrollbarThumbRow(totalLines);
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
            flatCacheDirty = true;
        }
        int oldCount = currentAIBlock.lineCount();
        int oldTotal = totalContentLines();
        int oldViewportStart = viewportStart;
        int prevThumb = scrollbarThumbRow(oldTotal);
        int aiWidth = Math.max(1, terminal.getWidth() - 3); // "│ " prefix(2) + scrollbar(1)
        currentAIBlock.append(text, aiWidth);
        appendLinesToFlatCache(currentAIBlock, oldCount);
        if (autoScroll) {
            scrollToBottom();
            int scrolled = viewportStart - oldViewportStart;
            int firstDirty = Math.max(0, oldTotal - 1); // 原末行可能被续写
            markViewportScrollAppend(scrolled, firstDirty, prevThumb);
        } else {
            int added = totalContentLines() - oldTotal;
            markViewportDiffRows(Math.max(0, oldTotal - 1), added + 1, prevThumb);
        }
    }

    private void ensureAIBlock() {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
            flatCacheDirty = true;
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

    private void appendThinking(String text) {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
            flatCacheDirty = true;
        }
        int oldCount = currentAIBlock.lineCount();
        int oldTotal = totalContentLines();
        int oldViewportStart = viewportStart;
        int prevThumb = scrollbarThumbRow(oldTotal);
        int thinkWidth = Math.max(1, terminal.getWidth() - 5); // "│ " prefix(2) + indent(2) + scrollbar(1)
        currentAIBlock.appendThinking(text, thinkWidth);
        appendLinesToFlatCache(currentAIBlock, oldCount);
        if (autoScroll) {
            scrollToBottom();
            int scrolled = viewportStart - oldViewportStart;
            int firstDirty = Math.max(0, oldTotal - 1);
            markViewportScrollAppend(scrolled, firstDirty, prevThumb);
        } else {
            int added = totalContentLines() - oldTotal;
            markViewportDiffRows(Math.max(0, oldTotal - 1), added + 1, prevThumb);
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
        flatCacheDirty = true;
        if (autoScroll) scrollToBottom();
        dirtyViewportFull();
    }

    // ===== scrolling =====

    private void scrollDelta(int delta) {
        int oldStart = viewportStart;
        viewportStart += delta;
        clampViewport();
        if (viewportStart != oldStart) {
            dirtyViewportFull();
        }
        autoScroll = viewportStart >= maxViewportStart();
    }

    private void scrollToBottom() { viewportStart = maxViewportStart(); }

    private int clampValue(int n) { return Math.max(0, Math.min(n, maxViewportStart())); }

    private void clampViewport() { viewportStart = Math.max(0, Math.min(viewportStart, maxViewportStart())); }

    private int maxViewportStart() { return Math.max(0, totalContentLines() - viewportHeight); }

    // ===== helpers =====

    private void rebuildFlatCache() {
        rebuildCount++;
        flatLineCache = new ArrayList<>();
        blockStartLines = new int[blocks.size()];
        int row = 0;
        for (int i = 0; i < blocks.size(); i++) {
            MessageBlock block = blocks.get(i);
            blockStartLines[i] = row;
            List<RenderedLine> lines = block.allLines();
            for (int j = 0; j < lines.size(); j++) {
                flatLineCache.add(new LineWithRole(lines.get(j), block.role(), j == 0));
            }
            row += lines.size();
        }
        flatCacheDirty = false;
    }

    /**
     * 末尾追加路径的增量缓存维护：只有最后一个 block 行数增长，
     * blockStartLines 不变，直接在 flatLineCache 尾部追加新行。
     * 缓存失效或 block 非末尾时回退全量重建。
     */
    private void appendLinesToFlatCache(MessageBlock block, int oldCount) {
        if (flatCacheDirty) {
            rebuildFlatCache();
            return;
        }
        if (blocks.isEmpty() || blocks.get(blocks.size() - 1) != block) {
            flatCacheDirty = true;
            rebuildFlatCache();
            return;
        }
        List<RenderedLine> lines = block.allLines();
        for (int j = oldCount; j < lines.size(); j++) {
            flatLineCache.add(new LineWithRole(lines.get(j), block.role(), j == 0));
        }
    }

    // ===== test observability (package-visible) =====

    int rebuildCount() {
        return rebuildCount;
    }

    void debugRebuildFlatCache() {
        rebuildFlatCache();
    }

    int debugTotalLines() {
        if (flatCacheDirty) rebuildFlatCache();
        return flatLineCache.size();
    }

    String debugLineText(int globalIndex) {
        if (flatCacheDirty) rebuildFlatCache();
        var lw = getLineWithRole(globalIndex);
        return lw == null ? null : lw.line().segments().toString();
    }

    private int totalContentLines() {
        if (flatCacheDirty) rebuildFlatCache();
        return flatLineCache.size();
    }

    private int blockToGlobalRow(MessageBlock block) {
        if (flatCacheDirty) rebuildFlatCache();
        int idx = blocks.indexOf(block);
        return idx >= 0 && idx < blockStartLines.length ? blockStartLines[idx] : 0;
    }

    private record LineWithRole(RenderedLine line, Role role, boolean isFirstLine) {}

    private LineWithRole getLineWithRole(int globalIndex) {
        if (flatCacheDirty) rebuildFlatCache();
        if (globalIndex >= 0 && globalIndex < flatLineCache.size()) {
            return flatLineCache.get(globalIndex);
        }
        return null;
    }

    private void reflowAll() {
        int reflowWidth = Math.max(1, terminal.getWidth() - 3); // conservative: 2-char prefix + scrollbar
        blocks.forEach(b -> b.reflow(reflowWidth));
        clampViewport();
        flatCacheDirty = true;
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
