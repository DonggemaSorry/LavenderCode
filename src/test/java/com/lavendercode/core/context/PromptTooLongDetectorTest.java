package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PromptTooLongDetectorTest {
    @Test
    void detectsKnownMessages() {
        assertThat(PromptTooLongDetector.isPromptTooLong("prompt_too_long", 400)).isTrue();
        assertThat(PromptTooLongDetector.isPromptTooLong("maximum context length exceeded", 400)).isTrue();
        assertThat(PromptTooLongDetector.isPromptTooLong("rate limited", 429)).isFalse();
    }
}
