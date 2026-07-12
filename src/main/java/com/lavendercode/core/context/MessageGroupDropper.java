package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import java.util.ArrayList;
import java.util.List;

public final class MessageGroupDropper {
    private MessageGroupDropper() {}

    public static List<List<Message>> group(List<Message> history) {
        List<List<Message>> groups = new ArrayList<>();
        List<Message> current = new ArrayList<>();
        for (Message m : history) {
            if (m.role() == Role.USER && !current.isEmpty()) {
                groups.add(new ArrayList<>(current));
                current.clear();
            }
            current.add(m);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    public static List<Message> flatten(List<List<Message>> groups) {
        List<Message> out = new ArrayList<>();
        for (List<Message> g : groups) {
            out.addAll(g);
        }
        return out;
    }

    public static List<List<Message>> dropOldest(List<List<Message>> groups, int count) {
        if (groups.isEmpty()) return groups;
        int drop = Math.min(count, groups.size());
        return new ArrayList<>(groups.subList(drop, groups.size()));
    }

    public static int ratioDropCount(int remaining) {
        return Math.max(1, (int) Math.ceil(remaining * ContextConstants.PTL_DROP_RATIO));
    }
}
