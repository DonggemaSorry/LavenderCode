package com.lavendercode.chat.terminal;

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

    private static final String TAGLINE = "\u7b49\u4f60\uff0c\u5728\u85b0\u8863\u8349\u76db\u5f00\u7684\u5730\u65b9";
    private static final String ORNAMENT = "\u2726";

    private static final int TYPEWRITER_MS = 90;
    private static final int HOLD_MS = 700;

    private LavenderSplash() {}

    private static final int[][] LAVENDER_PIXELS = {
    {0,0,0,0,0,0,0,0,0,0,0,0x8652D1,0x8A4CD1,0,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0xFCFFE9,0xAE8BE9,0xB88AE2,0x8460C4,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0,0x7241B7,0x4A229D,0x917DB8,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0xD7B8FB,0x9B6ADD,0x411C8D,0x7242BA,0x945CD9,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0x9060D0,0x492096,0x7C47C9,0x9261D4,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0xFFF1FF,0x6636AC,0x47208D,0xFFFAFF,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0xFEFFF9,0x8F5ED4,0,0x454E55,0x5B2FAB,0xFEFFF6,0x9661D5,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0xFEFDF8,0xA676E6,0x7546C8,0x546F42,0x5E36A6,0x874DC6,0x7B46CA,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0x9579C4,0x935ED0,0x441B93,0x925DD3,0x8551D0,0,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0xAE96D4,0x6434AA,0x4D2597,0x563890,0,0,0,0,0,0,0,0xBB96F3,0xF3E1FF,0},
    {0,0x8553D6,0xDDC4FF,0,0,0,0,0,0,0x9C62D3,0,0x3D4550,0xDCC1F8,0xDABFF6,0x9364D8,0,0,0,0,0,0,0x7C4BCB,0,0},
    {0xFDFCFA,0xBE98E1,0x8D58CC,0,0,0,0,0,0x844FD1,0xA575D9,0xC09EF4,0x4F6D51,0x8354BC,0x8952D2,0xAF80E6,0x6F44AF,0,0,0,0xFCF7FD,0xFFFFFA,0x5930AA,0xFFF9FF,0},
    {0,0x7043BA,0x4E259F,0,0,0,0,0,0,0x6B3FB0,0x824FC8,0x40198C,0x8049C9,0x8956CD,0x6338AB,0,0,0,0,0x8656C4,0xA274D7,0x431F8B,0x9765D6,0x9361DA},
    {0x8550D2,0xA577DA,0x3B1B86,0xA16BDA,0x8753D2,0,0,0,0,0,0x9C80CA,0x491E92,0x421C8D,0xE7D8F9,0,0,0,0,0,0,0x51299B,0x8050C0,0x6639B0,0xF6E0FF},
    {0x824FD0,0x8A59CC,0x9C6DD7,0x7543C0,0,0,0,0,0,0x8051D1,0,0x2B4B33,0x5C34A7,0,0x8351D6,0,0,0,0,0xC2ACDA,0,0x4D2997,0,0},
    {0,0,0x4F2595,0xFAF9FF,0xFCFFEF,0xFBFFF7,0,0,0x8249D4,0xB287E1,0x7B4DB9,0x496D3F,0x693CB1,0x7F4AD0,0xAF82DF,0xA17ECE,0,0,0,0x7D4BC8,0x9665D0,0xF3FFF5,0x804AD4,0x7A4EBF},
    {0,0x6643A7,0x839585,0,0x9A63E3,0x7B54C1,0,0,0xFDFCFA,0x6E3CBF,0x834FCB,0xAA7DDC,0x582BAA,0x7A4AC4,0x6C3ABD,0,0,0,0,0,0x4E2397,0x7246C1,0x6936B9,0xF8FCFF},
    {0,0x9661CD,0xA073D2,0x955ED3,0x7845BE,0x6442B0,0,0,0,0,0x582DA3,0x7D49C8,0x50279F,0x4D249A,0,0,0,0,0,0,0xEBE0FF,0x461D93,0xFDFFF3,0},
    {0,0xF5DDFF,0x7945C4,0x8D5AD1,0x5B2EA5,0,0,0,0xFBF9FF,0x8350CF,0xFCF8FF,0x411C85,0x421B85,0,0x8E56D1,0,0,0,0x8251CE,0x9868E0,0x595669,0,0x9F6DE0,0},
    {0,0xFFFEFA,0x481E8E,0x4D2597,0xAB9ADE,0xB697E3,0x8049CB,0,0x8350D1,0xB48CE5,0x7C4DD9,0x4A6940,0,0x814DCB,0xAA7DDC,0x724EAE,0,0,0x884CD2,0xAA7CE9,0x5B2EA5,0xBC90F2,0xBA93E6,0x9E67DF},
    {0,0xB785F4,0,0x556467,0xF8F7FC,0x8652CE,0xA26EDC,0,0,0xFFF9FF,0x6837B4,0x482CA7,0x804CCA,0x50289B,0xFFF6FF,0,0,0,0,0x4B2395,0x7441C4,0x4F2799,0xF9F2FA,0},
    {0,0x8452CB,0x8652D0,0x6B41BF,0x824AD1,0x8C56D0,0,0,0,0xFDFEF8,0x8A79AD,0x5428A1,0x411D89,0,0,0,0,0,0xFFFAEE,0,0x452286,0x3F2185,0,0},
    {0,0xF0CEFF,0x7744C5,0x58279D,0x7240BD,0x50289B,0,0,0,0x789B71,0xFBFBF9,0x305239,0,0,0,0,0,0x8753D2,0x9C6BD8,0x7D4EB8,0x3A6537,0x8C52DA,0xFFFCF7,0},
    {0,0,0,0x2E0E73,0x402981,0,0xA688D2,0xF8F9F4,0,0xF7FCF8,0x4D784B,0x7E9760,0,0,0x7DA161,0,0,0xFCF7FD,0x6C38BE,0x8E58D2,0x441E83,0xAD7DEA,0x8C56D2,0},
    {0,0,0xF9F8F4,0,0x496B4A,0xF9FFFB,0x8151CB,0xF7FBFA,0,0,0xFDFCF8,0x75965F,0,0xDEE9D9,0x547F54,0,0,0,0x623F9D,0x8D57D3,0x582CA8,0x5E30AC,0,0},
    {0,0,0x7F47CC,0xA073D8,0x7F53CC,0x8B53DA,0x6A39AC,0,0,0,0,0x709359,0xFEF9FF,0x6D925E,0xFFFEF9,0,0,0,0xFEF1FF,0x462687,0x401E8F,0xFBFFF0,0,0},
    {0,0,0,0x50279F,0x492299,0x6B38B7,0x471F99,0,0,0,0,0x779A58,0xFBF9FC,0x546E55,0,0,0,0xF8FFFC,0,0x486547,0,0xFAFEFF,0,0},
    {0,0,0,0xFAFAF8,0x4D2B99,0x42298F,0,0,0x85A775,0,0,0x80A361,0x6C9170,0,0,0,0,0x87A871,0,0x627555,0xFEEFFF,0x7D4ACB,0,0},
    {0,0,0,0,0,0x648A4F,0,0,0x86A468,0,0,0x789B5B,0,0,0x69916C,0,0,0x457150,0xFDFEF9,0x773FD4,0x6137B5,0,0,0},
    {0,0,0,0,0,0xF6FFF2,0x668F57,0xFDFFFA,0x557754,0,0x678F51,0x6B8F51,0,0xF8FFF7,0x587F4A,0,0,0,0x3F643B,0xFEF6FF,0xF6F7F2,0,0,0},
    {0,0,0,0x82A17F,0xF9FBF8,0,0x507C5B,0xF8FAF7,0xB6BABB,0,0x517655,0x628658,0,0x638560,0xFFF9FB,0,0,0,0x5B824D,0,0,0,0,0},
    {0,0,0,0x628469,0x7E9B6D,0,0xF5FBF9,0,0xAEB4B0,0,0xFDFCFA,0x668A4D,0,0x71945C,0,0,0,0x819F61,0,0,0,0,0,0},
    {0,0,0,0,0x92AE95,0x6B9357,0x7DA67E,0x5A805B,0,0,0,0x5A7F4B,0x5E8266,0x527C54,0,0x698D67,0xD6E4CA,0x518159,0,0xF9FFF8,0x648E68,0,0,0},
    {0,0,0,0,0,0x507856,0x668C53,0x385F42,0xE1E8E0,0x6F9353,0,0x668951,0x486D4C,0,0x688A58,0,0x648069,0,0x5E875D,0x7E9E6C,0,0,0,0},
    {0,0,0,0,0,0,0,0x497250,0x8B9F83,0x819A87,0x6C9259,0x648654,0x557546,0,0x678853,0,0xB6C9AD,0x5C8662,0x658A54,0x67826F,0,0,0,0},
    {0,0,0,0,0,0,0,0,0xABCFA1,0,0x426843,0x5D7D4C,0x3A6343,0x779176,0x577C46,0x7E9E77,0x416348,0xF3FFF3,0xFAFEFF,0,0,0,0,0},
    {0,0,0,0,0,0xB9D2BF,0x628859,0x627B65,0xD4ECD2,0x82936F,0xF7FEF6,0x395D43,0x3F6146,0x587C4E,0x8D9A90,0x55734F,0x90A994,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0x779765,0x79965E,0x4D7455,0,0x648955,0x2E553A,0x3C6545,0x78947B,0,0x86A085,0x8FAC6A,0x758E71,0,0,0,0,0},
    {0,0,0,0,0,0,0,0xEFF5F3,0xEAF2E3,0x3C5E43,0x6A8C5A,0x4A6F4E,0x22462C,0x22492E,0xA3C0A4,0x527151,0x537A4D,0xEAF6EC,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0x3C6046,0x274D36,0x395C3E,0x405F40,0x3F6B4A,0,0,0,0,0,0,0,0,0},
    {0,0,0,0,0,0,0,0,0,0,0xF6F6F4,0x668657,0x3A5C43,0x4D6650,0,0,0,0,0,0,0,0,0,0}
    };

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
                    screen.append("\u00b7", night.foreground(55, 45, 78));
                } else {
                    screen.append(" ", night);
                }
            }
            if (row < rows - 1) {
                screen.append('\n');
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
                    line.append("\u2588", AttributedStyle.DEFAULT.foreground(r, g, b));
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
