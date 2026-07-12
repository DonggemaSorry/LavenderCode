package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AutoCompactCircuitBreakerTest {
    @Test
    void tripsAfterThreeFailures() {
        AutoCompactCircuitBreaker breaker = new AutoCompactCircuitBreaker();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.isTripped()).isFalse();
        breaker.recordFailure();
        assertThat(breaker.isTripped()).isTrue();
        assertThat(breaker.failureCount()).isEqualTo(3);
    }

    @Test
    void successResetsFailureCountAndTrip() {
        AutoCompactCircuitBreaker breaker = new AutoCompactCircuitBreaker();
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.isTripped()).isTrue();

        breaker.recordSuccess();

        assertThat(breaker.isTripped()).isFalse();
        assertThat(breaker.failureCount()).isZero();
    }
}
