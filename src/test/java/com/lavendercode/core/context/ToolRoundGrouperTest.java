package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolRoundGrouperTest {
    @Test
    void groupsAssistantToolCallsWithFollowingToolMessages() {
        var history = List.of(
            new Message(Role.USER, "hi"),
            Message.assistantWithTools(List.of(new ToolCall("c1", "read_file", Map.of()))),
            Message.toolResult("c1", ToolResult.success("ok", "aaa")),
            Message.toolResult("c2", ToolResult.success("ok", "bbb")),
            new Message(Role.ASSISTANT, "done")
        );
        List<ToolRound> rounds = ToolRoundGrouper.group(history);
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).entries()).hasSize(2);
        assertThat(rounds.get(0).entries().get(0).toolCallId()).isEqualTo("c1");
        assertThat(rounds.get(0).entries().get(0).utf8Bytes())
            .isEqualTo("aaa".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void skipsAssistantWithoutToolCalls() {
        var history = List.of(new Message(Role.ASSISTANT, "plain"));
        assertThat(ToolRoundGrouper.group(history)).isEmpty();
    }
}
