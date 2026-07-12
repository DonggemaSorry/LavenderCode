package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RecentTailSelectorTest {
    @Test
    void selectsAtLeastFiveSmallMessages() {
        TokenEstimator estimator = new TokenEstimator();
        RecentTailSelector selector = new RecentTailSelector(estimator);
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            history.add(new Message(Role.USER, "msg-" + i));
        }

        List<Message> tail = selector.select(history);

        assertThat(tail).hasSizeGreaterThanOrEqualTo(ContextConstants.RECENT_TAIL_MIN_MESSAGES);
        assertThat(tail.get(0).content()).isEqualTo("msg-0");
        assertThat(tail.get(tail.size() - 1).content()).isEqualTo("msg-7");
    }

    @Test
    void stopsWhenTokenThresholdReached() {
        TokenEstimator estimator = new TokenEstimator();
        estimator.replaceAnchor(0, 0, 0, 0);
        RecentTailSelector selector = new RecentTailSelector(estimator);
        List<Message> history = new ArrayList<>();
        String big = "X".repeat(8_000);
        for (int i = 0; i < 12; i++) {
            history.add(new Message(Role.USER, big + i));
        }

        List<Message> tail = selector.select(history);

        assertThat(tail.size()).isLessThan(history.size());
        assertThat(tail.size()).isGreaterThanOrEqualTo(ContextConstants.RECENT_TAIL_MIN_MESSAGES);
    }

    @Test
    void doesNotStartWithOrphanToolMessage() {
        TokenEstimator estimator = new TokenEstimator();
        RecentTailSelector selector = new RecentTailSelector(estimator);
        List<Message> history = List.of(
            new Message(Role.USER, "start"),
            Message.assistantWithTools(List.of(new ToolCall("c1", "read_file", Map.of("path", "a.txt")))),
            Message.toolResult("c1", ToolResult.success("ok", "file-body")),
            new Message(Role.ASSISTANT, "done")
        );

        List<Message> tail = selector.select(history);

        assertThat(tail.get(0).role()).isNotEqualTo(Role.TOOL);
    }
}
