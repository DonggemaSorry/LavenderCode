package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SessionIdGeneratorTest {
    @Test
    void formatIsEpochDashAlphanumeric() {
        String id = SessionIdGenerator.generate();
        assertThat(id).matches("\\d{10,}-[a-z0-9]{6}");
    }
}
