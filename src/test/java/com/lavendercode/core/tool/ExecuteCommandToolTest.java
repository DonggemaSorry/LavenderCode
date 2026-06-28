package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExecuteCommandToolTest {
    ExecuteCommandTool enabledTool = new ExecuteCommandTool(true, 30, 10000);
    ExecuteCommandTool disabledTool = new ExecuteCommandTool(false, 30, 10000);

    @Test
    void echoCommand() {
        var r = enabledTool.execute(Map.of("command", "echo hello"));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("hello");
    }

    @Test
    void commandDisabled() {
        var r = disabledTool.execute(Map.of("command", "echo test"));
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("COMMAND_DISABLED");
    }

    @Test
    void invalidParameters() {
        var r = enabledTool.execute(Map.of());
        assertThat(r.errorCategory()).isEqualTo("INVALID_PARAMETER");
    }
}
