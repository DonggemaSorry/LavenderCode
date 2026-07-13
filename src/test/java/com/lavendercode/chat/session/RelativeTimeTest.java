package com.lavendercode.chat.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeTimeTest {
    @Test
    void formatsHoursAgo() {
        Instant now = Instant.parse("2026-07-13T01:00:00Z");

        assertThat(RelativeTime.format(now.minusSeconds(3 * 60 * 60), now))
            .isEqualTo("3 hours ago");
    }

    @Test
    void formatsSingularDayAgo() {
        Instant now = Instant.parse("2026-07-13T01:00:00Z");

        assertThat(RelativeTime.format(now.minusSeconds(24 * 60 * 60), now))
            .isEqualTo("1 day ago");
    }
}
