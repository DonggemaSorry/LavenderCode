package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForkMessageBuilder {

    private ForkMessageBuilder() {}

    public static List<Message> build(List<Message> parent, String prompt) {
        List<Message> forked = new ArrayList<>();
        for (Message m : parent) {
            forked.add(copyMessage(m));
        }
        appendPlaceholderToolResults(forked);
        forked.add(new Message(Role.USER, ForkBoilerplate.format(prompt)));
        return forked;
    }

    public static boolean historyContainsBoilerplate(List<Message> messages) {
        if (messages == null) {
            return false;
        }
        for (Message m : messages) {
            if (m.role() == Role.USER && m.content() != null
                && m.content().contains(ForkBoilerplate.TAG)) {
                return true;
            }
        }
        return false;
    }

    private static void appendPlaceholderToolResults(List<Message> forked) {
        if (forked.isEmpty()) {
            return;
        }
        Message last = forked.get(forked.size() - 1);
        if (last.role() != Role.ASSISTANT || last.toolCalls() == null || last.toolCalls().isEmpty()) {
            return;
        }
        Set<String> answered = new HashSet<>();
        for (Message m : forked) {
            if (m.role() == Role.TOOL && m.toolCallId() != null) {
                answered.add(m.toolCallId());
            }
        }
        for (ToolCall tc : last.toolCalls()) {
            if (!answered.contains(tc.id())) {
                forked.add(Message.toolResult(
                    tc.id(),
                    ToolResult.success("placeholder", "[fork placeholder: tool not completed]")));
            }
        }
    }

    private static Message copyMessage(Message m) {
        return new Message(m.role(), m.content(), m.toolCalls(), m.toolResults(), m.toolCallId());
    }
}
