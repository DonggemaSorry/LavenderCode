package com.lavendercode.core.provider;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StreamEventUsageTest {
    @Test
    void fourArgConstructorWorks() {
        var u = new StreamEvent.Usage(10, 5, 3, 2);
        assertThat(u.inputTokens()).isEqualTo(10);
        assertThat(u.outputTokens()).isEqualTo(5);
        assertThat(u.cacheCreationTokens()).isEqualTo(3);
        assertThat(u.cacheReadTokens()).isEqualTo(2);
    }

    @Test
    void twoArgConstructorDefaultsToZero() {
        var u = new StreamEvent.Usage(10, 5);
        assertThat(u.cacheCreationTokens()).isZero();
        assertThat(u.cacheReadTokens()).isZero();
    }

    @Test
    void usageIsStreamEvent() {
        var u = new StreamEvent.Usage(1, 1);
        assertThat(u).isInstanceOf(StreamEvent.class);
    }
}
