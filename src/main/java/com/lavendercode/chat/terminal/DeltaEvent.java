package com.lavendercode.chat.terminal;

import java.util.Objects;

public sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.Thinking,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {

    record Content(String text) implements DeltaEvent {
        public Content { Objects.requireNonNull(text); }
    }

    record Thinking(String text) implements DeltaEvent {
        public Thinking { Objects.requireNonNull(text); }
    }

    record Complete() implements DeltaEvent {}

    record Error(String message, int statusCode) implements DeltaEvent {
        public Error { Objects.requireNonNull(message); }
    }

    record Usage(int inputTokens, int outputTokens) implements DeltaEvent {}
}
