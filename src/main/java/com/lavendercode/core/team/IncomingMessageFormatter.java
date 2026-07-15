package com.lavendercode.core.team;

import java.util.List;

/** Formats unread mailbox messages into an <incoming-messages> reminder. */
public final class IncomingMessageFormatter {
    private IncomingMessageFormatter() {}

    public static String format(List<MailMessage> unread) {
        if (unread == null || unread.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<incoming-messages>\n收到 ").append(unread.size()).append(" 条新消息:\n");
        for (int i = 0; i < unread.size(); i++) {
            MailMessage m = unread.get(i);
            String content = m.content() == null ? "" : m.content();
            if (content.length() > 200) {
                content = content.substring(0, 200);
            }
            sb.append('[').append(i + 1).append("] 来自 ").append(m.from())
                .append("(type=").append(m.type()).append(",ts=").append(m.timestamp()).append("): ")
                .append(m.summary() == null ? "" : m.summary()).append('\n')
                .append("    ").append(content).append('\n');
        }
        sb.append("</incoming-messages>");
        return sb.toString();
    }
}
