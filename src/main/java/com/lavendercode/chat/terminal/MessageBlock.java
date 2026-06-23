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
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n') {
                flushLineToOutput(currentLine.toString(), width, out);
                currentLine.setLength(0);
            } else {
                currentLine.append(c);
            }
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
                    AttributedStyle.DEFAULT.background(68, 68, 68))));
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

        // Zero-width: control chars, format chars, non-spacing marks
        int gc = Character.getType(codePoint);
        if (gc == Character.CONTROL || gc == Character.FORMAT
                || gc == Character.NON_SPACING_MARK || gc == Character.ENCLOSING_MARK) {
            return 0;
        }

        return 1;
    }

    // --- Inner types ---

    private abstract static sealed class Segment
            permits ContentSegment, ThinkingSegment {
        final List<RenderedLine> lines = new ArrayList<>();
    }

    private static final class ContentSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }

    private static final class ThinkingSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }
}
