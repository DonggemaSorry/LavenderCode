package com.lavendercode.chat.terminal;

/** Maps ANSI/CSI key sequences to scroll key codes. */
final class CsiKeyDecoder {

    static final int KEY_PAGE_UP = -1001;
    static final int KEY_PAGE_DOWN = -1002;
    static final int KEY_SCROLL_UP = -1003;
    static final int KEY_SCROLL_DOWN = -1004;
    static final int KEY_SCROLL_TOP = -1005;
    static final int KEY_SCROLL_BOTTOM = -1006;
    static final int KEY_WHEEL_UP = -1007;
    static final int KEY_WHEEL_DOWN = -1008;
    static final int KEY_SHIFT_TAB = -1009;
    static final int KEY_BRACKETED_PASTE = -2000;

    private CsiKeyDecoder() {}

    static int decodeCsi(String params, char terminator) {
        return switch (terminator) {
            case 'A' -> KEY_SCROLL_UP;
            case 'B' -> KEY_SCROLL_DOWN;
            case 'H' -> KEY_SCROLL_TOP;
            case 'F' -> KEY_SCROLL_BOTTOM;
            case 'Z' -> KEY_SHIFT_TAB;
            case '~' -> switch (params) {
                case "5" -> KEY_PAGE_UP;
                case "6" -> KEY_PAGE_DOWN;
                case "1" -> KEY_SCROLL_TOP;
                case "4" -> KEY_SCROLL_BOTTOM;
                default -> 0;
            };
            default -> 0;
        };
    }

    static int decodeSs3(char code) {
        return switch (code) {
            case 'A' -> KEY_SCROLL_UP;
            case 'B' -> KEY_SCROLL_DOWN;
            case 'H' -> KEY_SCROLL_TOP;
            case 'F' -> KEY_SCROLL_BOTTOM;
            default -> 0;
        };
    }

    static int decodeMouse(String params) {
        if (params.isEmpty()) {
            return 0;
        }
        int buttonEnd = params.indexOf(';');
        String buttonPart = buttonEnd >= 0 ? params.substring(0, buttonEnd) : params;
        try {
            int button = Integer.parseInt(buttonPart);
            return switch (button) {
                case 62, 64 -> KEY_WHEEL_UP;
                case 63, 65 -> KEY_WHEEL_DOWN;
                default -> 0;
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** @return scroll command arg for {@link InputEvent.CommandType#SCROLL}, or {@code null} */
    static String toScrollCommand(int key) {
        return switch (key) {
            case KEY_SCROLL_UP, KEY_WHEEL_UP -> "up";
            case KEY_SCROLL_DOWN, KEY_WHEEL_DOWN -> "down";
            case KEY_PAGE_UP -> "page-up";
            case KEY_PAGE_DOWN -> "page-down";
            case KEY_SCROLL_TOP -> "top";
            case KEY_SCROLL_BOTTOM -> "bottom";
            default -> null;
        };
    }
}
