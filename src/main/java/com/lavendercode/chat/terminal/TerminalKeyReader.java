package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;

import java.io.IOException;

/** Reads keys, scroll sequences, and bracketed paste from the terminal. */
final class TerminalKeyReader {

    private static final long ESC_TIMEOUT_MS = 50;
    private static final String BRACKETED_PASTE_ON = "\033[?2004h";
    private static final String BRACKETED_PASTE_OFF = "\033[?2004l";
    /** Sentinel returned by {@link #readEscapeSequence()} for Alt+Enter. */
    private static final int ALT_ENTER_NEWLINE = -3000;

    private final Terminal terminal;

    TerminalKeyReader(Terminal terminal) {
        this.terminal = terminal;
    }

    void enableBracketedPaste() {
        terminal.writer().print(BRACKETED_PASTE_ON);
        terminal.flush();
    }

    void disableBracketedPaste() {
        terminal.writer().print(BRACKETED_PASTE_OFF);
        terminal.flush();
    }

    TerminalInput readInput() throws IOException {
        int c = terminal.reader().read();
        if (c == '\r') {
            consumeLfIfPresent();
            return new TerminalInput.Submit();
        }
        if (c == '\n') {
            return new TerminalInput.Newline();
        }
        if (c != 27) {
            return new TerminalInput.Character(c);
        }

        int esc = readEscapeSequence();
        if (esc == ALT_ENTER_NEWLINE) {
            return new TerminalInput.Newline();
        }
        String scroll = scrollCommandFor(esc);
        if (scroll != null) {
            return new TerminalInput.Scroll(scroll);
        }
        if (esc == CsiKeyDecoder.KEY_BRACKETED_PASTE) {
            return new TerminalInput.Paste(readBracketedPasteContent());
        }
        if (esc == CsiKeyDecoder.KEY_SHIFT_TAB) {
            return new TerminalInput.Special(TerminalInput.SpecialKey.SHIFT_TAB);
        }
        return new TerminalInput.Escape();
    }

    static String scrollCommandFor(int key) {
        return CsiKeyDecoder.toScrollCommand(key);
    }

    private void consumeLfIfPresent() throws IOException {
        if (terminal.reader().peek(ESC_TIMEOUT_MS) == '\n') {
            terminal.reader().read(ESC_TIMEOUT_MS);
        }
    }

    private String readBracketedPasteContent() throws IOException {
        StringBuilder pasted = new StringBuilder();
        while (true) {
            int c = terminal.reader().read();
            if (c == 27) {
                if (isPasteEndMarker()) {
                    break;
                }
                pasted.append((char) 27);
                continue;
            }
            if (c == '\r') {
                consumeLfIfPresent();
                pasted.append('\n');
                continue;
            }
            if (c >= 0) {
                pasted.append((char) c);
            }
        }
        return pasted.toString();
    }

    private boolean isPasteEndMarker() throws IOException {
        int next = timedRead();
        if (next != '[') {
            return false;
        }
        StringBuilder params = new StringBuilder();
        while (true) {
            int c = timedRead();
            if (c < 0) {
                return false;
            }
            char ch = (char) c;
            if (ch == '~') {
                return "201".equals(params.toString());
            }
            params.append(ch);
        }
    }

    private int readEscapeSequence() throws IOException {
        int next = timedRead();
        if (next < 0) {
            return 27;
        }
        if (next == '\n' || next == '\r') {
            // Alt+Enter — newline without submit
            if (next == '\r') consumeLfIfPresent();
            return ALT_ENTER_NEWLINE;
        }
        return switch (next) {
            case '[' -> readCsiSequence();
            case 'O' -> readSs3Sequence();
            case '<' -> readMouseSequence();
            default -> 27;
        };
    }

    private int readCsiSequence() throws IOException {
        StringBuilder params = new StringBuilder();
        while (true) {
            int c = timedRead();
            if (c < 0) {
                return 27;
            }
            char ch = (char) c;
            if (isCsiTerminator(ch)) {
                if (ch == '~' && "200".equals(params.toString())) {
                    return CsiKeyDecoder.KEY_BRACKETED_PASTE;
                }
                int decoded = CsiKeyDecoder.decodeCsi(params.toString(), ch);
                return decoded != 0 ? decoded : 27;
            }
            params.append(ch);
        }
    }

    private int readSs3Sequence() throws IOException {
        int c = timedRead();
        if (c < 0) {
            return 27;
        }
        int decoded = CsiKeyDecoder.decodeSs3((char) c);
        return decoded != 0 ? decoded : 27;
    }

    private int readMouseSequence() throws IOException {
        StringBuilder params = new StringBuilder();
        while (true) {
            int c = timedRead();
            if (c < 0) {
                return 27;
            }
            char ch = (char) c;
            if (ch == 'M' || ch == 'm') {
                int decoded = CsiKeyDecoder.decodeMouse(params.toString());
                return decoded != 0 ? decoded : 27;
            }
            params.append(ch);
        }
    }

    private static boolean isCsiTerminator(char ch) {
        return (ch >= 'A' && ch <= 'Z') || ch == '~' || ch == 'M' || ch == 'm';
    }

    private int timedRead() throws IOException {
        return terminal.reader().read(ESC_TIMEOUT_MS);
    }
}
