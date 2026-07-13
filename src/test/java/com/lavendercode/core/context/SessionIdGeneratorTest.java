package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdGeneratorTest {
    @Test
    void formatIsDateTimeDashHex4() {
        String id = SessionIdGenerator.generate();

        assertThat(id).matches("\\d{8}-\\d{6}-[0-9a-f]{4}");
        assertThat(SessionIdGenerator.isNewFormat(id)).isTrue();
    }

    @Test
    void rejectsLegacyIds() {
        assertThat(SessionIdGenerator.isNewFormat("20260101-120000-abcd")).isTrue();
        assertThat(SessionIdGenerator.isNewFormat("legacy-session")).isFalse();
        assertThat(SessionIdGenerator.isNewFormat("1717000000-abc123")).isFalse();
    }
}
