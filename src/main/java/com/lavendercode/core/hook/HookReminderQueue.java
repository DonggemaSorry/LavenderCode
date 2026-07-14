package com.lavendercode.core.hook;

import java.util.*;

public final class HookReminderQueue {
    private final List<String> items = new ArrayList<>();

    public void add(String text) { items.add(text); }

    public List<String> drain() {
        var result = List.copyOf(items);
        items.clear();
        return result;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}
