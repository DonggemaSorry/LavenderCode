package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ToolRoundGrouper {
    private ToolRoundGrouper() {}

    public static List<ToolRound> group(List<Message> history) {
        List<ToolRound> rounds = new ArrayList<>();
        int i = 0;
        while (i < history.size()) {
            Message m = history.get(i);
            if (m.role() == Role.ASSISTANT && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<ToolResultEntry> entries = new ArrayList<>();
                int j = i + 1;
                while (j < history.size() && history.get(j).role() == Role.TOOL) {
                    Message toolMsg = history.get(j);
                    String content = toolContent(toolMsg);
                    int bytes = content.getBytes(StandardCharsets.UTF_8).length;
                    entries.add(new ToolResultEntry(j, toolMsg.toolCallId(), content, bytes));
                    j++;
                }
                rounds.add(new ToolRound(i, entries));
                i = j;
            } else {
                i++;
            }
        }
        return rounds;
    }

    private static String toolContent(Message toolMsg) {
        if (toolMsg.toolResults() == null || toolMsg.toolResults().isEmpty()) return "";
        ToolResult r = toolMsg.toolResults().get(0);
        if (r.content() != null) return r.content();
        return r.summary() != null ? r.summary() : "";
    }
}
