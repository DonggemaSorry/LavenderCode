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
        last.lines.clear();
        wrapAndColor(last.rawText.toString(), terminalWidth, last.lines);
        recalcLineCount();
        return lineCount() - oldCount;
    }

    public int appendThinking(String text, int terminalWidth) {
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
                wrapAndColor(cs.rawText.toString(), terminalWidth, cs.lines);
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
            segments.add(new ContentSegment());
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

    private void wrapAndColor(String raw, int width, List<RenderedLine> out) {
        StringBuilder currentLine = new StringBuilder();
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (cp == '\n') {
                flushLineToOutput(currentLine.toString(), width, out);
                currentLine.setLength(0);
            } else {
                currentLine.appendCodePoint(cp);
            }
            i += charCount;
        }
        if (currentLine.length() > 0) {
            flushLineToOutput(currentLine.toString(), width, out);
        }
    }

    private void flushLineToOutput(String line, int width, List<RenderedLine> out) {
        if (line.startsWith("```")) {
            out.add(new RenderedLine(new AttributedString(line,
                    AttributedStyle.DEFAULT.foreground(136, 136, 136))));
            inCodeBlock = !inCodeBlock;
            return;
        }
        wrapByDisplayWidth(line, width, out, false);
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
