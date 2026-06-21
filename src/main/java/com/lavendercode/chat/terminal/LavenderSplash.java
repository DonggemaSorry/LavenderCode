package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

/**
 * Poetic startup splash: a composited lavender meadow and tagline on twilight background.
 */
public final class LavenderSplash {

    private static final int BG_R = 14;
    private static final int BG_G = 10;
    private static final int BG_B = 24;

    private static final int ART_WIDTH = 24;
    private static final int ART_HEIGHT = 41;

    /** Horizontal advance between stems; smaller values overlap more (24 = full width, no overlap). */
    private static final int FLOWER_STEP = 9;
    /** Minimum stems across the meadow. */
    private static final int MIN_MEADOW_FLOWERS = 3;
    /** Back-row stems sit higher and appear softer for depth. */
    private static final int BACK_ROW_LIFT = -8;
    private static final float BACK_LAYER_BRIGHTNESS = 0.58f;
    private static final int[] ROW_OFFSET_PATTERN = {4, 0, -3, 2, -4, 1, -2, 3, -1, 1};

    private static final String TAGLINE_FIRST = "\u7b49\u4f60\uff0c";
    private static final String TAGLINE_SECOND = "\u5728\u85b0\u8863\u8349\u76db\u5f00\u7684\u5730\u65b9";
    private static final String ORNAMENT = "\u2726";

    private static final int TYPEWRITER_MS = 90;
    private static final int HOLD_MS = 700;
    /** Anchor meadow near the top so tagline stays on screen. */
    private static final int TOP_MARGIN = 1;
    private static final int BOTTOM_MARGIN = 2;
    /** Blank rows between the meadow canvas and ornament / tagline. */
    private static final int GAP_AFTER_MEADOW = 4;
    private static final int GAP_AFTER_ORNAMENT = 1;
    private static final int GAP_BETWEEN_TAGLINES = 1;

    private LavenderSplash() {}

    record FlowerPlacement(int col, int rowOffset, float brightness) {}

    record MeadowCanvas(int[][] pixels, int height) {}

    record SplashLayout(int meadowStartRow, int ornamentRow, int firstTextRow, int secondTextRow,
                        int visibleMeadowHeight) {}

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
        MeadowCanvas meadow = composeMeadow(cols);
        if (rows < 8 || meadow == null) {
            return;
        }

        drawStarryBackground(terminal, rows, cols);

        SplashLayout layout = computeLayout(rows, meadow);
        MeadowCanvas visibleMeadow = clipToHeight(meadow, layout.visibleMeadowHeight());

        revealMeadow(terminal, layout.meadowStartRow(), visibleMeadow);

        int ornamentCol = Math.max(0, (cols - displayWidth(ORNAMENT)) / 2);
        clearRow(terminal, layout.ornamentRow(), cols);
        writeLine(terminal, layout.ornamentRow(), ornamentCol,
            styled(ORNAMENT, 120, 90, 160, false));

        clearRow(terminal, layout.firstTextRow(), cols);
        typewriterTagline(terminal, layout.firstTextRow(), cols, TAGLINE_FIRST);

        clearRow(terminal, layout.secondTextRow(), cols);
        typewriterTagline(terminal, layout.secondTextRow(), cols, TAGLINE_SECOND);

        Thread.sleep(HOLD_MS);
    }

    static SplashLayout computeLayout(int terminalRows, MeadowCanvas meadow) {
        int footerHeight = GAP_AFTER_MEADOW + 1 + GAP_AFTER_ORNAMENT
            + 1 + GAP_BETWEEN_TAGLINES + 1 + BOTTOM_MARGIN;
        int maxMeadowHeight = Math.max(1, terminalRows - TOP_MARGIN - footerHeight);
        int visibleMeadowHeight = Math.min(meadow.height(), maxMeadowHeight);

        int meadowStartRow = TOP_MARGIN;
        int ornamentRow = meadowStartRow + visibleMeadowHeight + GAP_AFTER_MEADOW;
        int firstTextRow = ornamentRow + 1 + GAP_AFTER_ORNAMENT;
        int secondTextRow = firstTextRow + 1 + GAP_BETWEEN_TAGLINES;
        return new SplashLayout(meadowStartRow, ornamentRow, firstTextRow, secondTextRow, visibleMeadowHeight);
    }

    static MeadowCanvas clipToHeight(MeadowCanvas meadow, int maxRows) {
        if (maxRows >= meadow.height()) {
            return meadow;
        }
        int width = meadow.pixels()[0].length;
        int[][] clipped = new int[maxRows][width];
        System.arraycopy(meadow.pixels(), 0, clipped, 0, maxRows);
        return new MeadowCanvas(clipped, maxRows);
    }

    static int meadowSpan(int flowerCount) {
        if (flowerCount <= 0) {
            return 0;
        }
        return (flowerCount - 1) * FLOWER_STEP + ART_WIDTH;
    }

    static int computeMeadowFlowerCount(int terminalCols) {
        if (terminalCols < ART_WIDTH + 2) {
            return 0;
        }
        int count = MIN_MEADOW_FLOWERS;
        while (meadowSpan(count + 1) <= terminalCols - 2) {
            count++;
        }
        return count;
    }

    static MeadowCanvas composeMeadow(int terminalCols) {
        int frontCount = computeMeadowFlowerCount(terminalCols);
        if (frontCount == 0) {
            return null;
        }

        int span = meadowSpan(frontCount);
        int startCol = Math.max(0, (terminalCols - span) / 2);
        int halfStep = FLOWER_STEP / 2;

        java.util.ArrayList<FlowerPlacement> placements = new java.util.ArrayList<>();

        int backCount = frontCount + 1;
        int backStart = startCol - halfStep;
        for (int i = 0; i < backCount; i++) {
            int col = backStart + i * FLOWER_STEP;
            int rowOffset = BACK_ROW_LIFT + ROW_OFFSET_PATTERN[(i + 3) % ROW_OFFSET_PATTERN.length];
            placements.add(new FlowerPlacement(col, rowOffset, BACK_LAYER_BRIGHTNESS));
        }

        for (int i = 0; i < frontCount; i++) {
            int col = startCol + i * FLOWER_STEP;
            int rowOffset = ROW_OFFSET_PATTERN[i % ROW_OFFSET_PATTERN.length];
            placements.add(new FlowerPlacement(col, rowOffset, 1.0f));
        }

        int minOffset = Integer.MAX_VALUE;
        int maxOffset = Integer.MIN_VALUE;
        for (FlowerPlacement placement : placements) {
            minOffset = Math.min(minOffset, placement.rowOffset());
            maxOffset = Math.max(maxOffset, placement.rowOffset() + ART_HEIGHT);
        }

        int height = maxOffset - minOffset;
        int[][] canvas = new int[height][terminalCols];

        for (FlowerPlacement placement : placements) {
            int topRow = placement.rowOffset() - minOffset;
            paintFlower(canvas, height, terminalCols, placement.col(), topRow, placement.brightness());
        }

        return trimEmptyRows(canvas);
    }

    static MeadowCanvas trimEmptyRows(int[][] canvas) {
        int height = canvas.length;
        if (height == 0) {
            return new MeadowCanvas(canvas, 0);
        }
        int width = canvas[0].length;
        int first = 0;
        int last = height - 1;
        while (first < height && isEmptyRow(canvas[first])) {
            first++;
        }
        while (last > first && isEmptyRow(canvas[last])) {
            last--;
        }
        if (first > last) {
            return new MeadowCanvas(canvas, height);
        }
        int trimmedHeight = last - first + 1;
        int[][] trimmed = new int[trimmedHeight][width];
        for (int row = 0; row < trimmedHeight; row++) {
            trimmed[row] = canvas[first + row].clone();
        }
        return new MeadowCanvas(trimmed, trimmedHeight);
    }

    private static boolean isEmptyRow(int[] row) {
        for (int rgb : row) {
            if (rgb != 0) {
                return false;
            }
        }
        return true;
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

    private static void paintFlower(int[][] canvas, int height, int width,
                                    int leftCol, int topRow, float brightness) {
        for (int row = 0; row < ART_HEIGHT; row++) {
            int canvasRow = topRow + row;
            if (canvasRow < 0 || canvasRow >= height) {
                continue;
            }
            for (int col = 0; col < ART_WIDTH; col++) {
                int rgb = LAVENDER_PIXELS[row][col];
                if (rgb == 0) {
                    continue;
                }
                int canvasCol = leftCol + col;
                if (canvasCol < 0 || canvasCol >= width) {
                    continue;
                }
                canvas[canvasRow][canvasCol] = scaleRgb(rgb, brightness);
            }
        }
    }

    private static int scaleRgb(int rgb, float brightness) {
        int r = Math.round(((rgb >> 16) & 0xFF) * brightness);
        int g = Math.round(((rgb >> 8) & 0xFF) * brightness);
        int b = Math.round((rgb & 0xFF) * brightness);
        return (r << 16) | (g << 8) | b;
    }

    private static void revealMeadow(Terminal terminal, int startRow, MeadowCanvas meadow)
            throws InterruptedException {
        AttributedStyle night = AttributedStyle.DEFAULT.background(BG_R, BG_G, BG_B);
        int[][] pixels = meadow.pixels();
        int height = meadow.height();
        int width = pixels[0].length;

        for (int row = 0; row < height; row++) {
            AttributedStringBuilder line = new AttributedStringBuilder(width);
            for (int col = 0; col < width; col++) {
                int rgb = pixels[row][col];
                if (rgb == 0) {
                    line.append(" ", night);
                } else {
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    line.append("\u2588", AttributedStyle.DEFAULT.foreground(r, g, b));
                }
            }
            terminal.puts(InfoCmp.Capability.cursor_address, startRow + row, 0);
            terminal.writer().print(line.toAnsi());
            terminal.flush();
            Thread.sleep(18);
        }
    }

    private static void typewriterTagline(Terminal terminal, int textRow, int cols, String text)
            throws InterruptedException {
        int textCol = Math.max(0, (cols - displayWidth(text)) / 2);
        StringBuilder typed = new StringBuilder();
        int charIndex = 0;
        int totalChars = text.codePointCount(0, text.length());

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
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

    private static void clearRow(Terminal terminal, int row, int cols) {
        AttributedStyle night = AttributedStyle.DEFAULT.background(BG_R, BG_G, BG_B);
        AttributedStringBuilder line = new AttributedStringBuilder(cols);
        for (int col = 0; col < cols; col++) {
            line.append(" ", night);
        }
        terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
        terminal.writer().print(line.toAnsi());
        terminal.flush();
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
