package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.HitlChoice;
import com.lavendercode.core.permission.HitlRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public sealed interface RenderEvent
    permits RenderEvent.AppendToMessage,
            RenderEvent.FinalizeMessage,
            RenderEvent.AddUserMessage,
            RenderEvent.AddSystemMessage,
            RenderEvent.ScrollTo,
            RenderEvent.ScrollDelta,
            RenderEvent.ScrollPageUp,
            RenderEvent.ScrollPageDown,
            RenderEvent.ScrollAutoReset,
            RenderEvent.ClearChat,
            RenderEvent.WindowResize,
            RenderEvent.StatusUpdate,
            RenderEvent.ToolCallRender,
            RenderEvent.ToolResultRender,
            RenderEvent.RefreshInputChrome,
            RenderEvent.UpdateInputDraft,
            RenderEvent.RefreshAll,
            RenderEvent.CompletionMenu,
            RenderEvent.CompletionEntry,
            RenderEvent.PermissionPrompt,
            RenderEvent.PermissionPromptDismiss,
            RenderEvent.Shutdown {

    record AppendToMessage(String text) implements RenderEvent {
        public AppendToMessage { Objects.requireNonNull(text); }
    }

    record FinalizeMessage() implements RenderEvent {}

    record AddUserMessage(String text) implements RenderEvent {
        public AddUserMessage { Objects.requireNonNull(text); }
    }

    record AddSystemMessage(String text) implements RenderEvent {
        public AddSystemMessage { Objects.requireNonNull(text); }
    }

    record ScrollTo(int lineIndex) implements RenderEvent {
        public ScrollTo {
            if (lineIndex < 0) throw new IllegalArgumentException("lineIndex must be >= 0");
        }
    }

    record ScrollDelta(int offset) implements RenderEvent {}

    record ScrollPageUp() implements RenderEvent {}

    record ScrollPageDown() implements RenderEvent {}

    record ScrollAutoReset() implements RenderEvent {}

    record ClearChat() implements RenderEvent {}

    record WindowResize(int cols, int rows) implements RenderEvent {
        public WindowResize {
            if (cols < 1) throw new IllegalArgumentException("cols must be >= 1");
            if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
        }
    }

    record StatusUpdate(
        String modeLabel,
        String modelName,
        String statusText,
        int tokenCount
    ) implements RenderEvent {
        public StatusUpdate {
            Objects.requireNonNull(modeLabel, "modeLabel must not be null");
            Objects.requireNonNull(modelName, "modelName must not be null");
        }
    }

    record PermissionPrompt(HitlRequest request, CompletableFuture<HitlChoice> future) implements RenderEvent {}

    record PermissionPromptDismiss() implements RenderEvent {}

    record RefreshInputChrome(CountDownLatch done) implements RenderEvent {
        public RefreshInputChrome() { this(null); }
    }

    record UpdateInputDraft(String draft, int cursorIndex, CountDownLatch done) implements RenderEvent {
        public UpdateInputDraft(String draft, int cursorIndex) {
            this(draft != null ? draft : "", cursorIndex, null);
        }

        public UpdateInputDraft {
            Objects.requireNonNull(draft);
            if (cursorIndex < 0 || cursorIndex > draft.length()) {
                throw new IllegalArgumentException("cursorIndex out of range");
            }
        }
    }

    record ToolCallRender(String toolCallId, String toolName, Map<String, Object> params, String status) implements RenderEvent {}
    record ToolResultRender(String toolCallId, String summary, boolean success, int contentLength) implements RenderEvent {}

    record RefreshAll() implements RenderEvent {}

    record CompletionMenu(
        List<CompletionEntry> entries,
        int selectedIndex,
        boolean visible
    ) implements RenderEvent {
        public CompletionMenu {
            Objects.requireNonNull(entries);
            entries = List.copyOf(entries);
            if (selectedIndex < 0) selectedIndex = 0;
        }
    }

    record CompletionEntry(
        String name,
        String description
    ) implements RenderEvent {
        public CompletionEntry {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
        }
    }

    record Shutdown() implements RenderEvent {}
}
