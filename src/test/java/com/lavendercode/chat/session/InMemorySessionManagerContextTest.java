package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionManagerContextTest {
    @Test
    void updateToolContentRewritesMatchingToolMessage() {
        SessionManager sm = new InMemorySessionManager();
        var tc = new ToolCall("tc1", "grep", Map.of());
        sm.addToolMessages(List.of(tc), List.of(ToolResult.success("s", "BIG")));
        sm.updateToolContent("tc1", "PREVIEW");
        Message tool = sm.getHistory().stream()
            .filter(m -> m.role() == Role.TOOL && "tc1".equals(m.toolCallId()))
            .findFirst().orElseThrow();
        assertThat(tool.toolResults().get(0).content()).isEqualTo("PREVIEW");
    }

    @Test
    void replaceHistoryReplacesEntireList() {
        SessionManager sm = new InMemorySessionManager();
        sm.addUserMessage("old");
        sm.replaceHistory(List.of(new Message(Role.USER, "new")));
        assertThat(sm.getHistory()).hasSize(1);
        assertThat(sm.getHistory().get(0).content()).isEqualTo("new");
    }
}
