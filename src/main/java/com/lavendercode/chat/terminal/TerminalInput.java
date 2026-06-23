package com.lavendercode.chat.terminal;

/** A single input action from the terminal (character, scroll, paste, submit). */
sealed interface TerminalInput {

    record Character(int code) implements TerminalInput {}

    record Scroll(String command) implements TerminalInput {}

    /** Bracketed or bulk paste — insert as text, do not interpret embedded Enter. */
    record Paste(String text) implements TerminalInput {}

    record Submit() implements TerminalInput {}

    record Newline() implements TerminalInput {}

    record Exit() implements TerminalInput {}
}
