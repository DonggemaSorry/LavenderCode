package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.ProviderConfig;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.List;

/**
 * Terminal-based provider selection UI with arrow-key navigation.
 * Blocks until the user makes a selection or exits.
 */
public final class ProviderSelector {

    private static final String VERSION = "2.0.0";
    private static final AttributedStyle TITLE_STYLE =
        AttributedStyle.DEFAULT.foreground(205, 133, 255).bold();
    private static final AttributedStyle SELECTED_STYLE =
        AttributedStyle.DEFAULT.foreground(205, 133, 255).bold();
    private static final AttributedStyle NORMAL_STYLE =
        AttributedStyle.DEFAULT.foreground(200, 200, 200);
    private static final AttributedStyle HINT_STYLE =
        AttributedStyle.DEFAULT.foreground(136, 136, 136);
    private static final AttributedStyle BODY_STYLE =
        AttributedStyle.DEFAULT.foreground(230, 220, 255);

    private ProviderSelector() {}

    public static ProviderConfig select(Terminal terminal, List<ProviderConfig> providers) {
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();

        int selectedIndex = 0;
        try {
            while (true) {
                drawScreen(terminal, providers, selectedIndex);
                int ch = terminal.reader().read();
                if (ch == '\r' || ch == '\n') {
                    break;  // Enter — confirm selection
                } else if (ch == 3) {
                    // Ctrl+C — exit
                    terminal.puts(InfoCmp.Capability.cursor_visible);
                    terminal.flush();
                    System.exit(0);
                } else if (ch == 0x1B) {
                    int next = terminal.reader().read(50);
                    if (next == '[') {
                        int dir = terminal.reader().read();
                        if (dir == 'A') {
                            selectedIndex = Math.max(0, selectedIndex - 1);
                        } else if (dir == 'B') {
                            selectedIndex = Math.min(providers.size() - 1, selectedIndex + 1);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Fall through, return at current index
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.flush();
        }

        return providers.get(selectedIndex);
    }

    private static void drawScreen(Terminal terminal, List<ProviderConfig> providers, int selected) {
        int w = terminal.getWidth();
        terminal.puts(InfoCmp.Capability.clear_screen);

        printCentered(terminal, 0, "LavenderCode v" + VERSION, w, TITLE_STYLE);
        String cwd = "cwd: " + System.getProperty("user.dir");
        printCentered(terminal, 1, truncatePath(cwd, w - 6), w, HINT_STYLE);
        printCentered(terminal, 3, "\u2500".repeat(Math.min(w, 80)), w, HINT_STYLE);
        printCentered(terminal, 5, "Select AI Provider:", w, BODY_STYLE);

        for (int i = 0; i < providers.size(); i++) {
            ProviderConfig pc = providers.get(i);
            String displayName = pc.name() != null ? pc.name() : pc.protocol() + "-" + pc.model();
            String line = (i == selected ? "  \u25CF " : "    ") + displayName + " (" + pc.model() + ")";
            AttributedStyle style = i == selected ? SELECTED_STYLE : NORMAL_STYLE;
            terminal.puts(InfoCmp.Capability.cursor_address, 7 + i, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().print(new AttributedString(line, style).toAnsi(terminal));
        }

        terminal.puts(InfoCmp.Capability.cursor_address, 9 + providers.size(), 0);
        String hint = "\u2191\u2193 select  Enter confirm  Ctrl+C exit";
        terminal.writer().print(new AttributedString(hint, HINT_STYLE).toAnsi(terminal));
        terminal.flush();
    }

    private static void printCentered(Terminal terminal, int row, String text, int width,
                                       AttributedStyle style) {
        int dw = displayWidth(text);
        int pad = Math.max(0, (width - dw) / 2);
        String padded = " ".repeat(pad) + text;
        terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
        terminal.puts(InfoCmp.Capability.clr_eol);
        terminal.writer().print(new AttributedString(padded, style).toAnsi(terminal));
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            if (cp >= 0x4E00 && cp <= 0x9FFF) w += 2;
            else if (cp >= 0xAC00 && cp <= 0xD7AF) w += 2;
            else if (cp >= 0xFF01 && cp <= 0xFF60) w += 2;
            else w += 1;
        }
        return w;
    }

    private static String truncatePath(String path, int maxWidth) {
        if (displayWidth(path) <= maxWidth) return path;
        int remaining = maxWidth - 1;
        return "\u2026" + path.substring(Math.max(0, path.length() - remaining));
    }
}
