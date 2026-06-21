package com.lavendercode.chat.terminal;

import java.util.ArrayList;
import java.util.List;

/** Lays out input draft text into screen rows inside the input panel. */
final class InputDraftLayout {

    record Line(String prefix, String text, boolean showCursor, int cursorCol) {}

    private record DisplayLine(String prefix, String text, int draftStart, int draftEnd) {}

    private InputDraftLayout() {}

    static int countDisplayLines(String draft, int terminalWidth) {
        if (draft.isEmpty()) {
            return 1;
        }
        return buildDisplayLines(draft, terminalWidth).size();
    }

    static int desiredEditRows(String draft, int terminalWidth, int terminalRows) {
        int lines = countDisplayLines(draft, terminalWidth);
        return Math.max(InputAreaLayout.MIN_EDIT_ROWS,
            Math.min(lines, InputAreaLayout.maxEditRows(terminalRows)));
    }

    static List<Line> format(String draft, int cursorIndex, int terminalWidth, int maxRows) {
        List<DisplayLine> display = buildDisplayLines(draft, terminalWidth);
        if (display.isEmpty()) {
            return List.of(new Line("> ", "", cursorIndex == 0, 0));
        }

        int cursorLine = 0;
        int cursorCol = 0;
        boolean found = false;
        for (int i = 0; i < display.size(); i++) {
            DisplayLine dl = display.get(i);
            if (cursorIndex >= dl.draftStart() && cursorIndex <= dl.draftEnd()) {
                cursorLine = i;
                cursorCol = Math.min(cursorIndex - dl.draftStart(), dl.text().length());
                found = true;
                break;
            }
        }
        if (!found) {
            cursorLine = display.size() - 1;
            cursorCol = display.getLast().text().length();
        }

        int start = windowStart(display.size(), maxRows, cursorLine);
        List<Line> result = new ArrayList<>();
        for (int i = start; i < display.size(); i++) {
            DisplayLine dl = display.get(i);
            boolean show = i == cursorLine;
            result.add(new Line(dl.prefix(), dl.text(), show, show ? cursorCol : 0));
        }
        return result;
    }

    private static int windowStart(int totalLines, int maxRows, int cursorLine) {
        if (totalLines <= maxRows) {
            return 0;
        }
        int start = Math.max(0, cursorLine - maxRows + 1);
        if (start + maxRows > totalLines) {
            start = totalLines - maxRows;
        }
        return start;
    }

    private static List<DisplayLine> buildDisplayLines(String draft, int terminalWidth) {
        List<DisplayLine> lines = new ArrayList<>();
        if (draft.isEmpty()) {
            return lines;
        }

        int draftPos = 0;
        String[] paragraphs = draft.split("\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            String prefix = lines.isEmpty() ? "> " : "  ";
            int firstWidth = Math.max(1, terminalWidth - prefix.length());
            int contWidth = Math.max(1, terminalWidth - 2);
            draftPos = appendWrapped(lines, prefix, paragraphs[p], firstWidth, contWidth, draftPos);
            if (p < paragraphs.length - 1) {
                lines.add(new DisplayLine("  ", "", draftPos, draftPos));
                draftPos += 1;
            }
        }
        return lines;
    }

    private static int appendWrapped(List<DisplayLine> lines, String firstPrefix, String text,
                                     int firstWidth, int contWidth, int draftPos) {
        if (text.isEmpty()) {
            lines.add(new DisplayLine(firstPrefix, "", draftPos, draftPos));
            return draftPos;
        }

        int offset = 0;
        boolean first = true;
        while (offset < text.length()) {
            String prefix = first ? firstPrefix : "  ";
            int width = first ? firstWidth : contWidth;
            int chunk = Math.min(width, text.length() - offset);
            String segment = text.substring(offset, offset + chunk);
            lines.add(new DisplayLine(prefix, segment, draftPos, draftPos + segment.length()));
            draftPos += segment.length();
            offset += chunk;
            first = false;
        }
        return draftPos;
    }
}
