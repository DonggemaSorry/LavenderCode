package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolCallTest {
    @Test
    void basic() {
        var c = new ToolCall("id", "read_file", Map.of("path", "/x"));
        assertThat(c.parseError()).isNull();
        assertThat(c.id()).isEqualTo("id");
        assertThat(c.name()).isEqualTo("read_file");
    }

    @Test
    void withParseError() {
        var c = new ToolCall("id", "f", Map.of()).withParseError("bad json");
        assertThat(c.parseError()).isEqualTo("bad json");
    }
}
