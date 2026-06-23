package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTimerTest {

    @Test
    void shouldReturnZeroBeforeStart() {
        ResponseTimer timer = new ResponseTimer();
        assertThat(timer.elapsedSeconds()).isEqualTo(0);
    }

    @Test
    void shouldReturnElapsedSeconds() throws InterruptedException {
        ResponseTimer timer = new ResponseTimer();
        timer.start();
        Thread.sleep(1100);
        long elapsed = timer.elapsedSeconds();
        assertThat(elapsed).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldStopAndPreserveTime() throws InterruptedException {
        ResponseTimer timer = new ResponseTimer();
        timer.start();
        Thread.sleep(500);
        timer.stop();
        long stopped = timer.elapsedSeconds();
        Thread.sleep(500);
        assertThat(timer.elapsedSeconds()).isEqualTo(stopped);
    }
}
