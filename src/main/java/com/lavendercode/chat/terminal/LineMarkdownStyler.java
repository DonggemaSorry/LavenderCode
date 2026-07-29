package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Styles a single completed markdown source line at stream time.
 * In native-scroll mode a printed line enters terminal history and can
 * never be repainted, so each line must be styled the moment it is
 * closed by a newline ("逐行即时着色").
 */
final class LineMarkdownStyler {

    private static final AttributedStyle RULE_STYLE =
        AttributedStyle.DEFAULT.foreground(136, 136, 136);

    private LineMarkdownStyler() {}

    /**
     * Renders one closed source line into styled, width-wrapped lines.
     * Never returns an empty list — callers rely on 1 source line
     * producing at least 1 rendered line.
     */
    static List<RenderedLine> style(String line, int width) {
        if (line.isEmpty()) {
            return List.of(new RenderedLine(new AttributedString("")));
        }
        if (isThematicBreak(line)) {
            int ruleWidth = Math.max(1, Math.min(width, 60));
            return List.of(new RenderedLine(
                new AttributedString("\u2500".repeat(ruleWidth), RULE_STYLE)));
        }
        List<RenderedLine> rendered = MarkdownRenderer.render(line, width);
        if (rendered.isEmpty()) {
            // Constructs the markdown walker drops (e.g. indented code blocks)
            // fall back to plain wrapped text so no content is ever lost.
            return plainWrap(line, width);
        }
        return rendered;
    }

    /** True when the line looks like a GFM table row (starts with '|'). */
    static boolean isTableRow(String line) {
        return line.strip().startsWith("|");
    }

    /** True when the line is a header/body separator such as | --- | :-: |. */
    static boolean isTableSeparator(String line) {
        String t = line.strip();
        if (!t.contains("-")) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != '|' && c != '-' && c != ':' && c != ' ') return false;
        }
        return true;
    }

    /**
     * Renders buffered table rows as an aligned box table when they form a
     * valid GFM table (header + separator); otherwise falls back to
     * per-line pass-through styling so no content is lost.
     */
    static List<RenderedLine> styleTable(List<String> rows, int width) {
        if (rows.size() >= 2 && isTableSeparator(rows.get(1))) {
            List<RenderedLine> rendered =
                MarkdownRenderer.render(String.join("\n", rows), width);
            if (!rendered.isEmpty()) {
                return rendered;
            }
        }
        List<RenderedLine> out = new ArrayList<>();
        for (String row : rows) {
            out.addAll(style(row, width));
        }
        return out;
    }

    /** Matches ---, ***, ___ (3+ repeats, spaces allowed) on their own line. */
    private static boolean isThematicBreak(String line) {
        String t = line.strip().replace(" ", "");
        if (t.length() < 3) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int i = 1; i < t.length(); i++) {
            if (t.charAt(i) != c) return false;
        }
        return true;
    }

    private static List<RenderedLine> plainWrap(String line, int width) {
        List<RenderedLine> out = new ArrayList<>();
        int start = 0;
        int cols = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int charCount = Character.charCount(cp);
            int w = MessageBlock.charDisplayWidth(cp);
            if (cols + w > width && start < i) {
                out.add(new RenderedLine(new AttributedString(line.substring(start, i))));
                start = i;
                cols = 0;
            }
            cols += w;
            i += charCount;
        }
        if (start < line.length()) {
            out.add(new RenderedLine(new AttributedString(line.substring(start))));
        }
        if (out.isEmpty()) {
            out.add(new RenderedLine(new AttributedString("")));
        }
        return out;
    }
}
