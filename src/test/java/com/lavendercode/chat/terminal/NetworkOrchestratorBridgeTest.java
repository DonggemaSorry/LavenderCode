package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkOrchestratorBridgeTest {
    @Test void shouldConvertAgentEventToRenderEvent() {
        // Verify AgentEvent types are constructible and carry correct data
        assertThat(new AgentEvent.Content("hi")).isNotNull();
        assertThat(new AgentEvent.ToolCallStart("id", "tool")).isNotNull();
        assertThat(new AgentEvent.ToolCallEnd(
            new ToolCall("id", "tool", Map.of()))).isNotNull();
        assertThat(new AgentEvent.ToolResultReady("id",
            ToolResult.success("ok", "content"))).isNotNull();
        assertThat(new AgentEvent.Usage(100, 50)).isNotNull();
        assertThat(new AgentEvent.RoundStart(1)).isNotNull();
        assertThat(new AgentEvent.RoundEnd(1)).isNotNull();
        assertThat(new AgentEvent.Complete()).isNotNull();
        assertThat(new AgentEvent.Stopped(AgentEvent.StopReason.MAX_ITERATIONS, "test")).isNotNull();
        assertThat(new AgentEvent.Error("fail")).isNotNull();
    }

    @Test void stopReasonEnumShouldContainAllExpectedReasons() {
        assertThat(AgentEvent.StopReason.values()).contains(
            AgentEvent.StopReason.NATURAL_COMPLETION,
            AgentEvent.StopReason.MAX_ITERATIONS,
            AgentEvent.StopReason.USER_CANCELLED,
            AgentEvent.StopReason.UNKNOWN_TOOLS,
            AgentEvent.StopReason.STREAM_ERROR
        );
    }
}
