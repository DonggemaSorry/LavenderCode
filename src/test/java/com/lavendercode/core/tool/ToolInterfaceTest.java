package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolInterfaceTest {
    @Test
    void anonymousToolWorks() {
        Tool t = new Tool() {
            @Override
            public String name() { return "test_tool"; }

            @Override
            public String description() { return "A test tool"; }

            @Override
            public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> params) {
                return ToolResult.success("done", "result");
            }
        };

        assertThat(t.name()).isEqualTo("test_tool");
        assertThat(t.description()).isEqualTo("A test tool");
        assertThat(t.parameters().type()).isEqualTo("object");
        assertThat(t.execute(Map.of()).success()).isTrue();
    }
}
