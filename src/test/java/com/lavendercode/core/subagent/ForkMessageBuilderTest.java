package com.lavendercode.core.subagent;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ForkMessageBuilderTest {

    @Test
    void appendsBoilerplateUserMessage() {
        List<Message> parent = List.of(
            new Message(Role.USER, "hello"),
            new Message(Role.ASSISTANT, "hi"));
        List<Message> forked = ForkMessageBuilder.build(parent, "sub task");
        assertThat(forked.subList(0, parent.size())).isEqualTo(parent);
        Message last = forked.get(forked.size() - 1);
        assertThat(last.content()).contains("<fork_boilerplate>");
        assertThat(last.content()).contains("sub task");
    }

    @Test
    void addsPlaceholderForUnpairedToolUse() {
        List<Message> parent = List.of(
            new Message(Role.USER, "go"),
            Message.assistantWithTools(List.of(
                new ToolCall("tc1", "read_file", Map.of("path", "a.txt")))));
        List<Message> forked = ForkMessageBuilder.build(parent, "sub task");
        assertThat(forked).anyMatch(m ->
            m.role() == Role.TOOL && "tc1".equals(m.toolCallId()));
    }

    @Test
    void historyContainsBoilerplateDetectsTag() {
        List<Message> msgs = List.of(
            new Message(Role.USER, ForkBoilerplate.format("task")));
        assertThat(ForkMessageBuilder.historyContainsBoilerplate(msgs)).isTrue();
    }
}
