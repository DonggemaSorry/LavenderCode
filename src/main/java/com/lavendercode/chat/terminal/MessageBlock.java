package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageBlock {

    private final UUID id;
    private final Role role;
    private boolean isComplete;
    private boolean inCodeBlock;
    private final List<Segment> segments;

    private int cachedLineCount = 0;
    private List<RenderedLine> cachedLines;
    private boolean linesDirty = true;

    public MessageBlock(Role role) {
        this.id = UUID.randomUUID();
        this.role = role;
        this.isComplete = false;
        this.inCodeBlock = false;
        this.segments = new ArrayList<>();
    }

    public Role role() { return role; }

    public boolean isComplete() {
        return isComplete;
    }

    public void markComplete() {
        this.isComplete = true;
    }

    public int lineCount() {
        return cachedLineCount;
    }

    private void recalcLineCount() {
        cachedLineCount = segments.stream().mapToInt(s -> s.lines.size()).sum();
    }

    public int append(String text, int terminalWidth) {
        linesDirty = true;
        int oldCount = lineCount();
        ensureLastContentSegment();
        ContentSegment last = (ContentSegment) segments.get(segments.size() - 1);
        last.rawText.append(text);
        rewrapFromStablePoint(last, terminalWidth);
        recalcLineCount();
        return lineCount() - oldCount;
    }

    public int appendThinking(String text, int terminalWidth) {
        closePendingTable(terminalWidth); // 表格块被 thinking 打断视为闭合
        linesDirty = true;
        int oldCount = lineCount();
        ThinkingSegment last = findLastThinkingSegment();
        if (last == null) {
            last = new ThinkingSegment();
            segments.add(last);
        }
        last.rawText.append(text);
        last.lines.clear();
        wrapAsThinking(last.rawText.toString(), terminalWidth, last.lines);
        recalcLineCount();
        return lineCount() - oldCount;
    }

    /**
     * Flushes any buffered table rows into rendered lines. Called when the
     * table block is closed by non-table content or the message stream ends.
     */
    public void closePendingTable(int terminalWidth) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i) instanceof ContentSegment cs) {
                if (!cs.tableBuffer.isEmpty()) {
                    linesDirty = true;
                    flushTableBuffer(cs, terminalWidth);
                    cs.partialStart = cs.lines.size();
                    recalcLineCount();
                }
                return;
            }
        }
    }

    public List<RenderedLine> allLines() {
        if (!linesDirty && cachedLines != null) {
            return cachedLines;
        }
        List<RenderedLine> result = new ArrayList<>();
        for (Segment seg : segments) {
            result.addAll(seg.lines);
        }
        cachedLines = result;
        linesDirty = false;
        return result;
    }

    public void reflow(int terminalWidth) {
        linesDirty = true;
        boolean savedInCode = inCodeBlock;
        inCodeBlock = false;
        for (Segment seg : segments) {
            seg.lines.clear();
            if (seg instanceof ContentSegment cs) {
                cs.stableRawLength = 0;
                cs.partialStart = 0;
                cs.inCodeBlockAtStable = inCodeBlock;
                cs.tableBuffer.clear(); // 重排时重新累积表格缓冲
                rewrapFromStablePoint(cs, terminalWidth);
                // 非末尾段或已完成消息：表格块已闭合，重排后直接输出
                boolean lastSegment = seg == segments.get(segments.size() - 1);
                if (!lastSegment || isComplete) {
                    flushTableBuffer(cs, terminalWidth);
                    cs.partialStart = cs.lines.size();
                }
            } else if (seg instanceof ThinkingSegment ts) {
                wrapAsThinking(ts.rawText.toString(), terminalWidth, ts.lines);
            }
        }
        inCodeBlock = savedInCode;
        recalcLineCount();
    }

    /**
     * Returns the concatenated raw text of all content segments,
     * suitable for markdown re-rendering.
     */
    public String getRawText() {
        StringBuilder sb = new StringBuilder();
        for (Segment seg : segments) {
            if (seg instanceof ContentSegment cs) {
                sb.append(cs.rawText);
            }
        }
        return sb.toString();
    }

    /**
     * Replaces the cached rendered lines with externally-styled lines
     * (e.g. from markdown rendering), bypassing wrapAndColor.
     */
    public void replaceLines(List<RenderedLine> newLines) {
        cachedLines = new ArrayList<>(newLines);
        cachedLineCount = newLines.size();
        linesDirty = false;
    }

    // --- Private helpers ---

    private void ensureLastContentSegment() {
        if (segments.isEmpty() || !(segments.get(segments.size() - 1) instanceof ContentSegment)) {
            ContentSegment cs = new ContentSegment();
            cs.inCodeBlockAtStable = inCodeBlock; // 继承当前代码块状态，保证跨段围栏连续
            segments.add(cs);
        }
    }

    private ThinkingSegment findLastThinkingSegment() {
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i) instanceof ThinkingSegment ts) {
                return ts;
            }
        }
        return null;
    }

    /**
     * 从稳定点增量重排：丢弃旧的未闭合尾行 → 恢复快照状态 →
     * wrap 新闭合的行（含 ``` 围栏检测）→ 重 wrap 末尾未闭合行。
     * 未闭合尾行不做围栏检测（等 \n 到达成为闭合行时才检测），
     * 避免部分围栏文本（如 "```ja"）被重复计数。
     */
    private void rewrapFromStablePoint(ContentSegment seg, int width) {
        seg.lines.subList(seg.partialStart, seg.lines.size()).clear();
        inCodeBlock = seg.inCodeBlockAtStable;

        String raw = seg.rawText.toString();
        int lineStart = seg.stableRawLength;
        for (int i = lineStart; i < raw.length(); i++) {
            if (raw.charAt(i) == '\n') {
                flushLineToOutput(seg, raw.substring(lineStart, i), width);
                lineStart = i + 1;
            }
        }
        seg.stableRawLength = lineStart;
        seg.inCodeBlockAtStable = inCodeBlock;
        seg.partialStart = seg.lines.size();

        if (lineStart < raw.length()) {
            wrapByDisplayWidth(raw.substring(lineStart), width, seg.lines, false);
        }
    }

    private void flushLineToOutput(ContentSegment seg, String line, int width) {
        List<RenderedLine> out = seg.lines;
        if (line.startsWith("```")) {
            flushTableBuffer(seg, width);
            out.add(new RenderedLine(new AttributedString(line,
                    AttributedStyle.DEFAULT.foreground(136, 136, 136))));
            inCodeBlock = !inCodeBlock;
            return;
        }
        if (inCodeBlock) {
            wrapByDisplayWidth(line, width, out, false);
            return;
        }
        if (LineMarkdownStyler.isTableRow(line)) {
            // 表格块缓冲到闭合再输出：行先积累，整表闭合后统一对齐渲染
            seg.tableBuffer.add(line);
            return;
        }
        flushTableBuffer(seg, width);
        // 逐行即时着色：行闭合时立即完成 Markdown 样式化，
        // 原生滚动模式下已打印的行无法回头重绘。
        out.addAll(LineMarkdownStyler.style(line, width));
    }

    private void flushTableBuffer(ContentSegment seg, int width) {
        if (seg.tableBuffer.isEmpty()) return;
        seg.lines.addAll(LineMarkdownStyler.styleTable(seg.tableBuffer, width));
        seg.tableBuffer.clear();
    }

    private void wrapAsThinking(String raw, int width, List<RenderedLine> out) {
        for (String line : raw.split("\n", -1)) {
            wrapByDisplayWidth(line, width, out, true);
        }
    }

    /** Wraps text at {@code width} display columns, accounting for CJK (2-wide) characters. */
    private void wrapByDisplayWidth(String line, int width, List<RenderedLine> out, boolean italic) {
        int len = line.length();
        int segStart = 0;
        int segColumns = 0;

        for (int i = 0; i < len; ) {
            int cp = line.codePointAt(i);
            int charCount = Character.charCount(cp);
            int colWidth = charDisplayWidth(cp);

            if (segColumns + colWidth > width && segStart < i) {
                // This character would overflow — flush the accumulated segment
                addWrappedLine(out, line.substring(segStart, i), italic);
                segStart = i;
                segColumns = 0;
            }
            segColumns += colWidth;
            i += charCount;
        }

        if (segStart < len) {
            addWrappedLine(out, line.substring(segStart), italic);
        }
    }

    private void addWrappedLine(List<RenderedLine> out, String text, boolean italic) {
        if (inCodeBlock) {
            out.add(new RenderedLine(new AttributedString(text,
                    AttributedStyle.DEFAULT.foreground(210, 210, 210).background(40, 44, 52))));
        } else if (italic) {
            out.add(new RenderedLine(new AttributedString(text,
                    AttributedStyle.DEFAULT.italic())));
        } else {
            out.add(new RenderedLine(new AttributedString(text)));
        }
    }

    /**
     * Returns the terminal display column width of a Unicode code point.
     * CJK ideographs, fullwidth forms, and Hangul are 2 columns; most others are 1.
     */
    static int charDisplayWidth(int codePoint) {
        // Ranges where each character occupies 2 terminal columns
        if (codePoint >= 0x1100 && codePoint <= 0x115f)  return 2; // Hangul Jamo
        if (codePoint == 0x2329 || codePoint == 0x232a)  return 2; // angle brackets
        if (codePoint >= 0x2e80 && codePoint <= 0xa4cf)  return 2; // CJK Radicals through Yi
        if (codePoint >= 0xac00 && codePoint <= 0xd7a3)  return 2; // Hangul Syllables
        if (codePoint >= 0xf900 && codePoint <= 0xfaff)  return 2; // CJK Compatibility Ideographs
        if (codePoint >= 0xfe30 && codePoint <= 0xfe6f)  return 2; // CJK Compatibility Forms
        if (codePoint >= 0xff01 && codePoint <= 0xff60)  return 2; // Fullwidth Forms
        if (codePoint >= 0xffe0 && codePoint <= 0xffe6)  return 2; // Fullwidth Signs
        if (codePoint >= 0x20000 && codePoint <= 0x2fffd) return 2; // SIP
        if (codePoint >= 0x30000 && codePoint <= 0x3fffd) return 2; // TIP

        // Emoji blocks
        if (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) return 2;
        if (codePoint >= 0x1FA00 && codePoint <= 0x1FAFF) return 2;
        if (codePoint >= 0x2600 && codePoint <= 0x27BF) return 2;
        if (codePoint >= 0x2300 && codePoint <= 0x23FF) return 2;
        if (codePoint >= 0xFE00 && codePoint <= 0xFE0F) return 0;  // variation selectors

        // Zero-width: control chars, format chars, non-spacing marks
        int gc = Character.getType(codePoint);
        if (gc == Character.CONTROL || gc == Character.FORMAT
                || gc == Character.NON_SPACING_MARK || gc == Character.ENCLOSING_MARK) {
            return 0;
        }

        return 1;
    }

    // --- Inner types ---

    /**
     * Appends or updates a tool execution row in this message block.
     * The first call creates a placeholder; subsequent calls with the same toolName
     * update params/results in place (Phase 2/3 of the 3-phase rendering).
     */
    public int appendToolRow(String toolName, String paramsSummary, String status,
                              String resultSummary, boolean success, int terminalWidth) {
        closePendingTable(terminalWidth); // 表格块被工具行打断视为闭合
        linesDirty = true;
        int oldCount = lineCount();
        ToolRowSegment seg = findOrCreateToolRow(toolName);
        if (paramsSummary != null) seg.paramsSummary = paramsSummary;
        if (status != null) seg.status = status;
        if (resultSummary != null) seg.resultSummary = resultSummary;
        seg.success = success;
        seg.lines.clear();
        rebuildToolRowLines(seg, terminalWidth);
        recalcLineCount();
        return lineCount() - oldCount;
    }

    private ToolRowSegment findOrCreateToolRow(String toolName) {
        for (Segment s : segments) {
            if (s instanceof ToolRowSegment trs && trs.toolName.equals(toolName)) {
                return trs;
            }
        }
        ToolRowSegment trs = new ToolRowSegment(toolName);
        segments.add(trs);
        return trs;
    }

    private void rebuildToolRowLines(ToolRowSegment seg, int terminalWidth) {
        // Line 1: ● {toolName}({paramsSummary})  → {status}
        StringBuilder header = new StringBuilder();
        header.append("● ");
        String shortName = switch (seg.toolName) {
            case "read_file" -> "Read";
            case "write_file" -> "Write";
            case "edit_file" -> "Edit";
            case "execute_command" -> "Bash";
            case "search_file" -> "Glob";
            case "search_content" -> "Grep";
            default -> seg.toolName;
        };
        if (seg.paramsSummary != null && !seg.paramsSummary.isEmpty()) {
            header.append(shortName).append("(").append(seg.paramsSummary).append(")");
        } else {
            header.append(shortName);
        }
        if (seg.status != null) {
            header.append("  → ").append(seg.status);
        }
        // Truncate to fit
        String headerStr = header.toString();
        if (headerStr.length() > terminalWidth) {
            headerStr = headerStr.substring(0, terminalWidth);
        }
        seg.lines.add(new RenderedLine(new AttributedString(headerStr,
            AttributedStyle.DEFAULT.foreground(136, 136, 136))));

        // Line 2 (if result available): "  {resultSummary}" in green/red
        if (seg.resultSummary != null) {
            AttributedStyle resultStyle = seg.success
                ? AttributedStyle.DEFAULT.foreground(100, 200, 100)
                : AttributedStyle.DEFAULT.foreground(220, 80, 80);
            String resultStr = "  " + seg.resultSummary;
            if (resultStr.length() > terminalWidth) {
                resultStr = resultStr.substring(0, terminalWidth);
            }
            seg.lines.add(new RenderedLine(new AttributedString(resultStr, resultStyle)));
        }
    }

    private abstract static sealed class Segment
            permits ContentSegment, ThinkingSegment, ToolRowSegment {
        final List<RenderedLine> lines = new ArrayList<>();
    }

    private static final class ContentSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
        final List<String> tableBuffer = new ArrayList<>(); // 已闭合但待整表渲染的表格行
        int stableRawLength;        // 最后一个已处理 '\n' 之后的位置
        int partialStart;           // 未闭合尾行在 lines 中的起始下标
        boolean inCodeBlockAtStable; // 稳定点处的代码块状态快照
    }

    private static final class ThinkingSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }

    private static final class ToolRowSegment extends Segment {
        final String toolName;
        String paramsSummary;
        String status = "准备中…";
        String resultSummary;
        boolean success = true;
        ToolRowSegment(String toolName) {
            this.toolName = toolName;
        }
    }
}
