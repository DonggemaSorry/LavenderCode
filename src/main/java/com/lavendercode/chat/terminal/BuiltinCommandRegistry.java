package com.lavendercode.chat.terminal;

import java.util.Map;
import java.util.stream.Collectors;

public final class BuiltinCommandRegistry {
    private static final Map<String, InputEvent.CommandType> COMMANDS = Map.ofEntries(
        Map.entry("exit", InputEvent.CommandType.EXIT),
        Map.entry("quit", InputEvent.CommandType.QUIT),
        Map.entry("clear", InputEvent.CommandType.CLEAR),
        Map.entry("help", InputEvent.CommandType.HELP),
        Map.entry("cancel", InputEvent.CommandType.CANCEL),
        Map.entry("plan", InputEvent.CommandType.PLAN),
        Map.entry("do", InputEvent.CommandType.DO),
        Map.entry("compact", InputEvent.CommandType.COMPACT)
    );

    private BuiltinCommandRegistry() {}

    public record ParseResult(InputEvent.CommandType type, String args, String hint) {}

    public static ParseResult parse(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("/")) {
            return new ParseResult(InputEvent.CommandType.UNKNOWN, trimmed, unknownHint(trimmed));
        }
        String body = trimmed.substring(1).trim();
        if (body.isEmpty()) {
            return new ParseResult(InputEvent.CommandType.HELP, "", helpText());
        }
        String cmd = body.split("\\s+", 2)[0].toLowerCase();
        String args = body.contains(" ") ? body.substring(cmd.length()).trim() : "";
        InputEvent.CommandType type = COMMANDS.get(cmd);
        if (type == null) {
            return new ParseResult(InputEvent.CommandType.UNKNOWN, "/" + cmd, unknownHint("/" + cmd));
        }
        return new ParseResult(type, args, null);
    }

    public static String helpText() {
        return """
            Commands:
              /exit       - Exit LavenderCode
              /clear      - Clear conversation history
              /help       - Show this help
              /plan       - Enter plan mode (read-only tools only)
              /do         - Exit plan mode and execute plan
              /compact    - Compress conversation context
              /cancel     - Cancel current request
            Keyboard:
              Shift+Tab   - Cycle permission mode
              ↑/↓         - Scroll one line
              PageUp/Down - Scroll one page
              Home/End    - Jump to top/bottom
              Mouse wheel - Scroll message area
              Esc         - Cancel current request (don't exit)
              Ctrl+C      - Exit LavenderCode
              Ctrl+D (empty) - Exit
              Enter       - Send message
              Ctrl+J      - Insert newline""";
    }

    private static String unknownHint(String cmd) {
        String available = COMMANDS.keySet().stream().sorted()
            .map(k -> "/" + k)
            .collect(Collectors.joining(", "));
        return "未知命令: " + cmd + "。可用: " + available;
    }
}
