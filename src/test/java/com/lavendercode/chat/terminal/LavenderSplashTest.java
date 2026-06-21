package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LavenderSplashTest {

    @Test
    void displayWidthCountsCjkAsTwoColumns() {
        assertThat(LavenderSplash.displayWidth("等你，在薰衣草盛开的地方")).isEqualTo(24);
        assertThat(LavenderSplash.displayWidth("ab")).isEqualTo(2);
    }
}
