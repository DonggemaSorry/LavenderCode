package com.lavendercode.chat.terminal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TokenAccumulatorTest {
    @Test
    void shouldAccumulateAcrossRounds() {
        TokenAccumulator acc = new TokenAccumulator();
        acc.add(100, 50);
        acc.add(200, 80);
        assertThat(acc.getTotalInput()).isEqualTo(300);
        assertThat(acc.getTotalOutput()).isEqualTo(130);
        assertThat(acc.getTotal()).isEqualTo(430);
    }
    @Test
    void shouldReset() {
        TokenAccumulator acc = new TokenAccumulator();
        acc.add(100, 50);
        acc.reset();
        assertThat(acc.getTotal()).isZero();
    }
}
