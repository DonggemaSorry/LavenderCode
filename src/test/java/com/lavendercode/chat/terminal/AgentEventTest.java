package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AgentEventTest {

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void shouldCreateAllEventTypes() {
        assertThat(new AgentEvent.Content("hi")).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.ToolCallStart("id", "read_file")).isInstanceOf(AgentEvent.class);
        ToolCall tc = new ToolCall("id", "read_file", Map.of());
        assertThat(new AgentEvent.ToolCallEnd(tc)).isInstanceOf(AgentEvent.class);
        ToolResult tr = ToolResult.success("ok", "content");
        assertThat(new AgentEvent.ToolResultReady("id", tr)).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.Usage(100, 50)).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.RoundStart(1)).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.RoundEnd(1)).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.Complete()).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.Stopped(AgentEvent.StopReason.MAX_ITERATIONS, "msg")).isInstanceOf(AgentEvent.class);
        assertThat(new AgentEvent.Error("err")).isInstanceOf(AgentEvent.class);
    }

    @Test
    void shouldCreateCancelledToolResult() {
        ToolResult r = ToolResult.cancelled("read_file");
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldExportReadOnlyToolsOnly() {
        ToolRegistry.register(new ReadOnlyDummy("ro"));
        ToolRegistry.register(new WriteDummy("rw"));
        var roDefs = ToolRegistry.exportReadOnly();
        assertThat(roDefs).hasSize(1)
            .extracting(ToolDefinition::name)
            .contains("ro");
    }

    static class ReadOnlyDummy implements Tool {
        private final String n;
        ReadOnlyDummy(String n) { this.n = n; }
        @Override public String name() { return n; }
        @Override public String description() { return "d"; }
        @Override public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), java.util.List.of());
        }
        @Override public ToolResult execute(java.util.Map<String, Object> p) {
            return ToolResult.success("ok", "");
        }
        @Override public boolean isReadOnly() { return true; }
    }

    static class WriteDummy implements Tool {
        private final String n;
        WriteDummy(String n) { this.n = n; }
        @Override public String name() { return n; }
        @Override public String description() { return "d"; }
        @Override public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), java.util.List.of());
        }
        @Override public ToolResult execute(java.util.Map<String, Object> p) {
            return ToolResult.success("ok", "");
        }
    }
}
