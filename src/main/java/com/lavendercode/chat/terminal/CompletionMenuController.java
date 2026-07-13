package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.CommandDefinition;
import com.lavendercode.core.command.CommandRegistry;

import java.util.List;
import java.util.Optional;

final class CompletionMenuController {
    private final CommandRegistry registry;
    private boolean active = false;
    private String currentPrefix = "";
    private List<CommandDefinition> candidates = List.of();
    private int selectedIndex = 0;

    CompletionMenuController(CommandRegistry registry) {
        this.registry = registry;
    }

    void onInputChanged(String buffer) {
        if (!buffer.startsWith("/") || buffer.contains("\n")) {
            active = false;
            return;
        }
        active = true;
        currentPrefix = buffer.substring(1);
        candidates = registry.visibleCommands().stream()
            .filter(d -> d.metadata().name()
                .startsWith(currentPrefix.toLowerCase()))
            .toList();
        if (selectedIndex >= candidates.size()) {
            selectedIndex = 0;
        }
    }

    void navigateUp() {
        if (candidates.isEmpty()) return;
        selectedIndex = (selectedIndex - 1 + candidates.size()) % candidates.size();
    }

    void navigateDown() {
        if (candidates.isEmpty()) return;
        selectedIndex = (selectedIndex + 1) % candidates.size();
    }

    Optional<String> executeSelected() {
        if (candidates.isEmpty()) return Optional.empty();
        String name = candidates.get(selectedIndex).metadata().name();
        active = false;
        return Optional.of("/" + name);
    }

    void dismiss() {
        active = false;
    }

    boolean isActive() {
        return active;
    }

    RenderEvent.CompletionMenu toRenderEvent() {
        if (!active) {
            return new RenderEvent.CompletionMenu(List.of(), 0, false);
        }
        var entries = candidates.stream()
            .map(d -> new RenderEvent.CompletionEntry(
                d.metadata().name(), d.metadata().description()))
            .toList();
        return new RenderEvent.CompletionMenu(entries, selectedIndex, true);
    }
}