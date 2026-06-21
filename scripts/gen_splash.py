#!/usr/bin/env python3
"""Generate LavenderSplash.java from pixel data."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
pixels_src = ROOT / "target" / "lavender_pixels.txt"
out = ROOT / "src" / "main" / "java" / "com" / "lavendercode" / "chat" / "terminal" / "LavenderSplash.java"

pixel_lines = []
for line in pixels_src.read_text(encoding="utf-8").splitlines():
    stripped = line.strip().rstrip(",")
    if stripped.startswith("{"):
        pixel_lines.append("    " + stripped)

pixel_block = "    private static final int[][] LAVENDER_PIXELS = {\n" + ",\n".join(pixel_lines) + "\n    };\n"

java = '''package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

/**
 * Poetic startup splash: pixel lavender art and tagline on a twilight background.
 */
public final class LavenderSplash {

    private static final int BG_R = 14;
    private static final int BG_G = 10;
    private static final int BG_B = 24;

    private static final int ART_WIDTH = 24;
    private static final int ART_HEIGHT = 41;

    private static final String TAGLINE = "\\u7b49\\u4f60\\uff0c\\u5728\\u85b0\\u8863\\u8349\\u76db\\u5f00\\u7684\\u5730\\u65b9";
    private static final String ORNAMENT = "\\u2726";

    private static final int TYPEWRITER_MS = 90;
    private static final int HOLD_MS = 700;

    private LavenderSplash() {}

''' + pixel_block + '''
    public static void show(Terminal terminal) throws InterruptedException {
        if (Boolean.getBoolean("lavendercode.skipSplash")) {
            return;
        }
        int rows = terminal.getHeight();
        int cols = terminal.getWidth();
        if (rows < 8 || cols < ART_WIDTH + 4) {
            return;
        }

        drawStarryBackground(terminal, rows, cols);

        int ornamentWidth = displayWidth(ORNAMENT);
        int taglineWidth = displayWidth(TAGLINE);
        int blockHeight = ART_HEIGHT + 2 + 1 + 1;
        int startRow = Math.max(0, (rows - blockHeight - 1) / 2);
        int artCol = Math.max(0, (cols - ART_WIDTH) / 2);

        revealArt(terminal, startRow, artCol);

        int ornamentRow = startRow + ART_HEIGHT + 2;
        int ornamentCol = Math.max(0, (cols - ornamentWidth) / 2);
        writeLine(terminal, ornamentRow, ornamentCol,
            styled(ORNAMENT, 120, 90, 160, false));

        int textRow = ornamentRow + 2;
        typewriterTagline(terminal, textRow, cols, taglineWidth);

        Thread.sleep(HOLD_MS);
    }

    static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += cp > 0xFF ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    private static void drawStarryBackground(Terminal terminal, int rows, int cols) {
        AttributedStyle night = AttributedStyle.DEFAULT.background(BG_R, BG_G, BG_B);
        AttributedStringBuilder screen = new AttributedStringBuilder(cols * rows + rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean star = ((row * 31 + col * 17) % 113) == 0 && (row + col) % 5 != 0;
                if (star) {
                    screen.append("\\u00b7", night.foreground(55, 45, 78));
                } else {
                    screen.append(" ", night);
                }
            }
            if (row < rows - 1) {
                screen.append('\\n');
            }
        }
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.writer().print(screen.toAnsi());
        terminal.flush();
    }

    private static void revealArt(Terminal terminal, int startRow, int artCol) throws InterruptedException {
        AttributedStyle night = AttributedStyle.DEFAULT.background(BG_R, BG_G, BG_B);
        for (int row = 0; row < ART_HEIGHT; row++) {
            AttributedStringBuilder line = new AttributedStringBuilder(ART_WIDTH);
            for (int col = 0; col < ART_WIDTH; col++) {
                int rgb = LAVENDER_PIXELS[row][col];
                if (rgb == 0) {
                    line.append(" ", night);
                } else {
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    line.append("\\u2588", AttributedStyle.DEFAULT.foreground(r, g, b));
                }
            }
            terminal.puts(InfoCmp.Capability.cursor_address, startRow + row, artCol);
            terminal.writer().print(line.toAnsi());
            terminal.flush();
            Thread.sleep(18);
        }
    }

    private static void typewriterTagline(Terminal terminal, int textRow, int cols, int taglineWidth)
            throws InterruptedException {
        int textCol = Math.max(0, (cols - taglineWidth) / 2);
        StringBuilder typed = new StringBuilder();
        int charIndex = 0;
        int totalChars = TAGLINE.codePointCount(0, TAGLINE.length());

        for (int i = 0; i < TAGLINE.length(); ) {
            int cp = TAGLINE.codePointAt(i);
            typed.appendCodePoint(cp);
            i += Character.charCount(cp);
            charIndex++;

            float t = totalChars <= 1 ? 1f : (charIndex - 1f) / (totalChars - 1);
            int r = lerp(130, 220, t);
            int g = lerp(100, 190, t);
            int b = lerp(170, 245, t);

            AttributedString line = styled(typed.toString(), r, g, b, true);
            terminal.puts(InfoCmp.Capability.cursor_address, textRow, textCol);
            terminal.writer().print(line.toAnsi());
            terminal.flush();
            Thread.sleep(TYPEWRITER_MS);
        }
    }

    private static AttributedString styled(String text, int r, int g, int b, boolean italic) {
        AttributedStyle style = AttributedStyle.DEFAULT.foreground(r, g, b).background(BG_R, BG_G, BG_B);
        if (italic) {
            style = style.italic();
        }
        return new AttributedString(text, style);
    }

    private static void writeLine(Terminal terminal, int row, int col, AttributedString text) {
        terminal.puts(InfoCmp.Capability.cursor_address, row, col);
        terminal.writer().print(text.toAnsi());
        terminal.flush();
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
'''

out.write_text(java, encoding="utf-8")
print(f"Wrote {out}")
