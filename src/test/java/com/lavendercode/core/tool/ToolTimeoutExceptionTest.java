package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ToolTimeoutExceptionTest {
    @Test
    void messageContainsToolNameAndTimeout() {
        var ex = new ToolTimeoutException("read_file", 30);
        assertThat(ex.getMessage()).contains("read_file");
        assertThat(ex.getMessage()).contains("30");
    }

    @Test
    void messageContainsTimedOutText() {
        var ex = new ToolTimeoutException("execute_command", 120);
        assertThat(ex.getMessage()).contains("timed out");
    }
}
