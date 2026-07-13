package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionListItem;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.List;

public final class SessionPicker {
    private static final int MAX_ROWS = 10;

    private SessionPicker() {
    }

    public static SessionListItem pick(Terminal terminal, List<SessionListItem> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return null;
        }

        Attributes saved = terminal.enterRawMode();
        TerminalKeyReader reader = new TerminalKeyReader(terminal);
        SessionPickerModel model = new SessionPickerModel(items);
        StringBuilder filter = new StringBuilder();
        try {
            render(terminal, model);
            while (true) {
                TerminalInput input = reader.readInput();
                switch (input) {
                    case TerminalInput.Submit() -> {
                        clear(terminal);
                        return model.selectedItem();
                    }
                    case TerminalInput.Escape() -> {
                        clear(terminal);
                        return null;
                    }
                    case TerminalInput.Scroll(var command) -> {
                        if ("up".equals(command)) {
                            model.moveUp();
                        } else if ("down".equals(command)) {
                            model.moveDown();
                        }
                        render(terminal, model);
                    }
                    case TerminalInput.Character(var code) -> {
                        if (code == 127 || code == 8) {
                            if (!filter.isEmpty()) {
                                filter.deleteCharAt(filter.length() - 1);
                                model.setFilter(filter.toString());
                                render(terminal, model);
                            }
                            continue;
                        }
                        if (code >= 32) {
                            filter.append((char) code);
                            model.setFilter(filter.toString());
                            render(terminal, model);
                        }
                    }
                    case TerminalInput.Paste(var text) -> {
                        String sanitized = text.replace("\r", "").replace("\n", " ");
                        filter.append(sanitized);
                        model.setFilter(filter.toString());
                        render(terminal, model);
                    }
                    default -> {
                    }
                }
            }
        } finally {
            terminal.setAttributes(saved);
        }
    }

    private static void render(Terminal terminal, SessionPickerModel model) {
        clear(terminal);
        terminal.writer().println("选择要恢复的会话（↑/↓ 导航，输入过滤，Enter 选择，Esc 取消）");
        terminal.writer().println("过滤: " + model.filter());
        terminal.writer().println();

        List<SessionListItem> visible = model.visibleItems();
        if (visible.isEmpty()) {
            terminal.writer().println("  没有匹配的会话");
            terminal.flush();
            return;
        }

        int limit = Math.min(MAX_ROWS, visible.size());
        for (int i = 0; i < limit; i++) {
            SessionListItem item = visible.get(i);
            String marker = i == model.selectedIndex() ? "> " : "  ";
            terminal.writer().println(marker + item.title()
                + "  [" + item.relativeTime() + "]"
                + modelText(item)
                + "  " + item.sessionId());
        }
        if (visible.size() > limit) {
            terminal.writer().println("  ... 还有 " + (visible.size() - limit) + " 个结果，请继续过滤");
        }
        terminal.flush();
    }

    private static String modelText(SessionListItem item) {
        return item.model() == null || item.model().isBlank() ? "" : "  " + item.model();
    }

    private static void clear(Terminal terminal) {
        terminal.writer().print("\033[2J\033[H");
        terminal.flush();
    }
}
