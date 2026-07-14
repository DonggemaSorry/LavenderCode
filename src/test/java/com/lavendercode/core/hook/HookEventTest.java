package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookEventTest {
    @Test
    void elevenEventsExist() {
        assertThat(HookEvent.values()).hasSize(11);
    }

    @Test
    void interceptableEvents() {
        assertThat(HookEvent.PreToolUse.interceptable()).isTrue();
        assertThat(HookEvent.UserPromptSubmit.interceptable()).isTrue();
    }

    @Test
    void nonInterceptableEvents() {
        assertThat(HookEvent.SessionStart.interceptable()).isFalse();
        assertThat(HookEvent.PostToolUse.interceptable()).isFalse();
        assertThat(HookEvent.Stop.interceptable()).isFalse();
    }

    @Test
    void fromNameCaseSensitive() {
        assertThat(HookEvent.fromName("SessionStart")).isEqualTo(HookEvent.SessionStart);
        assertThat(HookEvent.fromName("PreToolUse")).isEqualTo(HookEvent.PreToolUse);
    }

    @Test
    void unknownEventThrows() {
        assertThatThrownBy(() -> HookEvent.fromName("UnknownEvent"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
