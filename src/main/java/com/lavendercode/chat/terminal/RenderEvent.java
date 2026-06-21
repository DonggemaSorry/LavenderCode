package com.lavendercode.chat.terminal;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public sealed interface RenderEvent
    permits RenderEvent.AppendToMessage,
            RenderEvent.FinalizeMessage,
            RenderEvent.AddUserMessage,
            RenderEvent.AddSystemMessage,
            RenderEvent.ThinkDelta,
            RenderEvent.ScrollTo,
            RenderEvent.ScrollDelta,
            RenderEvent.ScrollPageUp,
            RenderEvent.ScrollPageDown,
            RenderEvent.ScrollAutoReset,
            RenderEvent.ClearChat,
            RenderEvent.WindowResize,
            RenderEvent.ThemeChange,
            RenderEvent.StatusUpdate,
            RenderEvent.RefreshInputChrome,
            RenderEvent.UpdateInputDraft,
            RenderEvent.RefreshAll,
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

    record ThinkDelta(String text) implements RenderEvent {
        public ThinkDelta { Objects.requireNonNull(text); }
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

    record ThemeChange(Theme theme) implements RenderEvent {
        public ThemeChange { Objects.requireNonNull(theme); }
    }

    record StatusUpdate(String model, int tokenCount, boolean isEstimating) implements RenderEvent {
        public StatusUpdate { Objects.requireNonNull(model); }
    }

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

    record RefreshAll() implements RenderEvent {}

    record Shutdown() implements RenderEvent {}
}
