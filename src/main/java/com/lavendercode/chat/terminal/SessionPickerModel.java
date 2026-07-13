package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionListItem;

import java.util.List;
import java.util.Locale;

public final class SessionPickerModel {
    private final List<SessionListItem> allItems;
    private String filter = "";
    private List<SessionListItem> visibleItems;
    private int selectedIndex;

    public SessionPickerModel(List<SessionListItem> items) {
        this.allItems = items == null ? List.of() : List.copyOf(items);
        refreshVisibleItems();
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter;
        refreshVisibleItems();
    }

    public String filter() {
        return filter;
    }

    public List<SessionListItem> visibleItems() {
        return visibleItems;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public SessionListItem selectedItem() {
        if (selectedIndex < 0 || selectedIndex >= visibleItems.size()) {
            return null;
        }
        return visibleItems.get(selectedIndex);
    }

    public void moveUp() {
        move(-1);
    }

    public void moveDown() {
        move(1);
    }

    private void move(int delta) {
        if (visibleItems.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + delta, visibleItems.size());
    }

    private void refreshVisibleItems() {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            visibleItems = allItems;
        } else {
            visibleItems = allItems.stream()
                .filter(item -> matches(item, needle))
                .toList();
        }
        selectedIndex = visibleItems.isEmpty() ? -1 : Math.min(Math.max(selectedIndex, 0), visibleItems.size() - 1);
    }

    private static boolean matches(SessionListItem item, String needle) {
        return contains(item.sessionId(), needle)
            || contains(item.title(), needle)
            || contains(item.model(), needle)
            || contains(item.relativeTime(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
