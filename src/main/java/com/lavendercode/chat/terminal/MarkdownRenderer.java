package com.lavendercode.chat.terminal;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts markdown text to a list of styled {@link RenderedLine}s
 * using flexmark-java for parsing and JLine AttributedStyle for styling.
 */
public final class MarkdownRenderer {

    private static final Parser PARSER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
        ));
        PARSER = Parser.builder(options).build();
    }

    private MarkdownRenderer() {}

    public static List<RenderedLine> render(String markdown, int width) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }

        Document doc = PARSER.parse(markdown);
        LineAccumulator acc = new LineAccumulator(width);
        walkNode(doc, acc);
        // Flush any remaining content
        acc.finishLineIfNotEmpty();
        return acc.lines;
    }

    private static void walkNode(Node node, LineAccumulator acc) {
        if (node instanceof Paragraph) {
            walkChildren(node, acc);
            acc.finishLineIfNotEmpty();
        } else if (node instanceof Heading h) {
            acc.setCurrentStyle(HEADING_STYLE);
            walkChildren(node, acc);
            acc.setCurrentStyle(BASE_STYLE);
            acc.finishLineIfNotEmpty();
        } else if (node instanceof StrongEmphasis) {
            AttributedStyle saved = acc.currentStyle();
            acc.setCurrentStyle(BOLD_STYLE);
            walkChildren(node, acc);
            acc.setCurrentStyle(saved);
        } else if (node instanceof Emphasis) {
            AttributedStyle saved = acc.currentStyle();
            acc.setCurrentStyle(ITALIC_STYLE);
            walkChildren(node, acc);
            acc.setCurrentStyle(saved);
        } else if (node instanceof Code) {
            acc.append(node.getChars().toString(), CODE_STYLE);
        } else if (node instanceof FencedCodeBlock fcb) {
            String info = fcb.getInfo() != null ? fcb.getInfo().toString() : "";
            if (!info.isEmpty()) {
                acc.append(info, LANG_STYLE);
                acc.finishLine();
            }
            String code = fcb.getContentChars().toString();
            String[] codeLines = code.split("\n", -1);
            for (int i = 0; i < codeLines.length; i++) {
                acc.append(codeLines[i], CODE_STYLE);
                if (i < codeLines.length - 1 || !code.endsWith("\n")) {
                    acc.finishLine();
                }
            }
            if (code.endsWith("\n") && codeLines.length > 0) {
                acc.finishLine();
            }
        } else if (node instanceof BulletListItem) {
            acc.append("  " + BULLET + " ", BASE_STYLE);
            walkChildren(node, acc);
            acc.finishLineIfNotEmpty();
        } else if (node instanceof OrderedListItem oi) {
            String marker = oi.getOpeningMarker().toString();
            acc.append("  " + marker + " ", BASE_STYLE);
            walkChildren(node, acc);
            acc.finishLineIfNotEmpty();
        } else if (node instanceof BlockQuote) {
            acc.setBlockPrefix("| ");
            walkChildren(node, acc);
            acc.setBlockPrefix(null);
            acc.finishLineIfNotEmpty();
        } else if (node instanceof Strikethrough) {
            AttributedStyle saved = acc.currentStyle();
            acc.setCurrentStyle(STRIKE_STYLE);
            walkChildren(node, acc);
            acc.setCurrentStyle(saved);
        } else if (node instanceof com.vladsch.flexmark.ast.Text) {
            acc.append(node.getChars().toString(), acc.currentStyle());
        } else if (node instanceof SoftLineBreak) {
            acc.append(" ", acc.currentStyle());
        } else if (node instanceof HardLineBreak) {
            acc.finishLine();
        } else if (node instanceof TableBlock) {
            renderTable(node, acc);
        } else if (node instanceof TableSeparator) {
            // skip — handled by renderTable
        } else {
            walkChildren(node, acc);
        }
    }

    private static void walkChildren(Node node, LineAccumulator acc) {
        Node child = node.getFirstChild();
        while (child != null) {
            walkNode(child, acc);
            child = child.getNext();
        }
    }

    // --- Style constants ---
    private static final AttributedStyle BASE_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
    private static final AttributedStyle HEADING_STYLE = BASE_STYLE.bold().foreground(205, 0, 205);
    private static final AttributedStyle BOLD_STYLE = BASE_STYLE.bold();
    private static final AttributedStyle ITALIC_STYLE = BASE_STYLE.italic().foreground(0, 205, 205);
    private static final AttributedStyle CODE_STYLE =
        BASE_STYLE.foreground(210, 210, 210).background(40, 44, 52);
    private static final AttributedStyle LANG_STYLE =
        BASE_STYLE.foreground(136, 136, 136).background(40, 44, 52);
    private static final AttributedStyle STRIKE_STYLE = BASE_STYLE.crossedOut();
    private static final AttributedStyle TABLE_STYLE = BASE_STYLE.foreground(180, 180, 180);
    private static final String BULLET = "\u2022";  // bullet character

    // --- Table rendering ---

    private static void renderTable(Node tableBlock, LineAccumulator acc) {
        List<List<String>> headerRows = new ArrayList<>();
        List<List<String>> bodyRows = new ArrayList<>();
        List<TableCell.Alignment> alignments = new ArrayList<>();
        int cols = 0;

        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableHead) {
                Node row = child.getFirstChild();
                while (row != null) {
                    if (row instanceof TableRow) {
                        List<String> cells = new ArrayList<>();
                        Node cell = row.getFirstChild();
                        while (cell != null) {
                            if (cell instanceof TableCell tc) {
                                cells.add(collectText(tc));
                                if (alignments.size() < cells.size()) {
                                    alignments.add(tc.getAlignment());
                                }
                            }
                            cell = cell.getNext();
                        }
                        cols = Math.max(cols, cells.size());
                        headerRows.add(cells);
                    }
                    row = row.getNext();
                }
            } else if (child instanceof TableBody) {
                Node row = child.getFirstChild();
                while (row != null) {
                    if (row instanceof TableRow) {
                        List<String> cells = new ArrayList<>();
                        Node cell = row.getFirstChild();
                        while (cell != null) {
                            if (cell instanceof TableCell) {
                                cells.add(collectText(cell));
                            }
                            cell = cell.getNext();
                        }
                        cols = Math.max(cols, cells.size());
                        bodyRows.add(cells);
                    }
                    row = row.getNext();
                }
            }
            child = child.getNext();
        }

        if (cols == 0) return;

        // Normalize column count
        while (alignments.size() < cols) alignments.add(null);
        for (List<String> row : headerRows) while (row.size() < cols) row.add("");
        for (List<String> row : bodyRows)    while (row.size() < cols) row.add("");

        // Calculate column display widths (respect CJK 2-wide chars)
        int[] colWidths = new int[cols];
        for (List<String> row : headerRows)
            for (int c = 0; c < cols; c++)
                colWidths[c] = Math.max(colWidths[c], displayWidth(row.get(c)));
        for (List<String> row : bodyRows)
            for (int c = 0; c < cols; c++)
                colWidths[c] = Math.max(colWidths[c], displayWidth(row.get(c)));
        for (int c = 0; c < cols; c++)
            colWidths[c] = Math.max(1, colWidths[c]);

        int totalWidth = Arrays.stream(colWidths).sum() + (cols + 1);

        // Header
        for (List<String> row : headerRows) {
            addTableLine("\u2502", row, colWidths, alignments, acc);
            addHLine("\u251C", "\u253C", "\u2524", colWidths, totalWidth, acc);
        }
        // Body
        for (int i = 0; i < bodyRows.size(); i++) {
            addTableLine("\u2502", bodyRows.get(i), colWidths, alignments, acc);
            if (i < bodyRows.size() - 1) {
                addHLine("\u251C", "\u253C", "\u2524", colWidths, totalWidth, acc);
            }
        }
        // Bottom border
        addHLine("\u2514", "\u2534", "\u2518", colWidths, totalWidth, acc);
    }

    private static String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof com.vladsch.flexmark.ast.Text) {
                sb.append(child.getChars());
            } else if (child instanceof Code) {
                sb.append(child.getChars());
            } else if (child instanceof SoftLineBreak) {
                sb.append(' ');
            } else {
                sb.append(collectText(child));
            }
            child = child.getNext();
        }
        return sb.toString().trim();
    }

    private static void addTableLine(String border, List<String> cells,
            int[] colWidths, List<TableCell.Alignment> alignments, LineAccumulator acc) {
        StringBuilder sb = new StringBuilder();
        sb.append(border);
        for (int c = 0; c < cells.size(); c++) {
            sb.append(' ');
            sb.append(padCell(cells.get(c), colWidths[c], alignments.get(c)));
            sb.append(' ');
            sb.append(border);
        }
        acc.append(sb.toString(), TABLE_STYLE);
        acc.finishLine();
    }

    private static void addHLine(String left, String mid, String right,
            int[] colWidths, int totalWidth, LineAccumulator acc) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int c = 0; c < colWidths.length; c++) {
            if (c > 0) sb.append(mid);
            sb.append("\u2500".repeat(colWidths[c] + 2));
        }
        sb.append(right);
        acc.append(sb.toString(), TABLE_STYLE);
        acc.finishLine();
    }

    private static String padCell(String text, int width, TableCell.Alignment align) {
        int textWidth = displayWidth(text);
        int padding = Math.max(0, width - textWidth);
        if (align == TableCell.Alignment.CENTER) {
            int left = padding / 2;
            return " ".repeat(left) + text + " ".repeat(padding - left);
        } else if (align == TableCell.Alignment.RIGHT) {
            return " ".repeat(padding) + text;
        }
        return text + " ".repeat(padding);
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += LineAccumulator.charWidthStatic(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    // --- Internal line accumulator ---
    private static class LineAccumulator {
        final List<RenderedLine> lines = new ArrayList<>();
        final int width;
        final StringBuilder currentLine = new StringBuilder();
        final List<AttributedString> currentSegments = new ArrayList<>();
        AttributedStyle currentStyleFlag = BASE_STYLE;
        String blockPrefix;

        LineAccumulator(int width) { this.width = width; }

        AttributedStyle currentStyle() { return currentStyleFlag; }
        void setCurrentStyle(AttributedStyle s) { this.currentStyleFlag = s; }
        void setBlockPrefix(String p) { this.blockPrefix = p; }

        void append(String text, AttributedStyle style) {
            if (text == null || text.isEmpty()) return;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                int charCount = Character.charCount(cp);
                if (cp == '\n') {
                    addSegment(style);
                    finishLine();
                } else {
                    String ch = text.substring(i, i + charCount);
                    currentLine.append(ch);
                    currentSegments.add(new AttributedString(currentLine.toString(), style));
                    currentLine.setLength(0);
                    if (displayWidth() >= width) {
                        finishLine();
                    }
                }
                i += charCount;
            }
        }

        int displayWidth() {
            int w = 0;
            if (blockPrefix != null) {
                for (int i = 0; i < blockPrefix.length(); ) {
                    int cp = blockPrefix.codePointAt(i);
                    w += charWidth(cp);
                    i += Character.charCount(cp);
                }
            }
            for (AttributedString seg : currentSegments) {
                String s = seg.toString();
                if (s == null || s.isEmpty()) continue;
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    w += charWidth(cp);
                    i += Character.charCount(cp);
                }
            }
            for (int i = 0; i < currentLine.length(); ) {
                int cp = currentLine.codePointAt(i);
                w += charWidth(cp);
                i += Character.charCount(cp);
            }
            return w;
        }

        void finishLineIfNotEmpty() {
            if (!currentSegments.isEmpty() || currentLine.length() > 0) {
                finishLine();
            }
        }

        void finishLine() {
            if (currentSegments.isEmpty()) {
                currentSegments.add(new AttributedString("", BASE_STYLE));
            }
            List<AttributedString> copy = new ArrayList<>(currentSegments);
            if (blockPrefix != null && !blockPrefix.isEmpty()) {
                copy.add(0, new AttributedString(blockPrefix, BASE_STYLE));
            }
            lines.add(new RenderedLine(copy));
            currentSegments.clear();
            currentLine.setLength(0);
        }

        void addSegment(AttributedStyle style) {
            if (currentLine.length() > 0) {
                currentSegments.add(new AttributedString(currentLine.toString(), style));
                currentLine.setLength(0);
            }
        }

        static int charWidthStatic(int cp) {
            // 0-width: control, format, non-spacing marks
            int gc = Character.getType(cp);
            if (gc == Character.CONTROL || gc == Character.FORMAT
                    || gc == Character.NON_SPACING_MARK || gc == Character.ENCLOSING_MARK) {
                return 0;
            }
            // 2-width: CJK, Hangul, Fullwidth
            if (cp >= 0x4E00 && cp <= 0x9FFF) return 2;
            if (cp >= 0xAC00 && cp <= 0xD7AF) return 2;
            if (cp >= 0xFF01 && cp <= 0xFF60) return 2;
            if (cp >= 0x1100 && cp <= 0x115f) return 2;
            if (cp >= 0x2e80 && cp <= 0xa4cf) return 2;
            if (cp >= 0xf900 && cp <= 0xfaff) return 2;
            if (cp >= 0xfe30 && cp <= 0xfe6f) return 2;
            if (cp >= 0xffe0 && cp <= 0xffe6) return 2;
            if (cp >= 0x20000 && cp <= 0x2fffd) return 2;
            if (cp >= 0x30000 && cp <= 0x3fffd) return 2;
            // 2-width: Emoji blocks (most modern emoji are wide)
            if (cp >= 0x1F300 && cp <= 0x1F9FF) return 2;
            if (cp >= 0x1FA00 && cp <= 0x1FAFF) return 2;
            if (cp >= 0x2600 && cp <= 0x27BF) return 2;   // Misc symbols (includes some emoji)
            if (cp >= 0x2300 && cp <= 0x23FF) return 2;   // Misc technical (includes some emoji)
            // Variation selectors are 0-width
            if (cp >= 0xFE00 && cp <= 0xFE0F) return 0;
            return 1;
        }

        int charWidth(int cp) {
            return charWidthStatic(cp);
        }
    }
}
