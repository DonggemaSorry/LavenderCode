package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MessageGroupDropperTest {
    @Test
    void groupsByUserBoundaries() {
        List<Message> history = List.of(
            new Message(Role.USER, "u1"),
            new Message(Role.ASSISTANT, "a1"),
            new Message(Role.USER, "u2"),
            new Message(Role.ASSISTANT, "a2")
        );
        List<List<Message>> groups = MessageGroupDropper.group(history);
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0)).extracting(Message::content).containsExactly("u1", "a1");
    }

    @Test
    void dropOldestRemovesLeadingGroups() {
        List<List<Message>> groups = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            groups.add(List.of(new Message(Role.USER, "u" + i)));
        }
        List<List<Message>> dropped = MessageGroupDropper.dropOldest(groups, 1);
        assertThat(dropped).hasSize(4);
        assertThat(dropped.get(0).get(0).content()).isEqualTo("u1");
    }

    @Test
    void ratioDropCountDropsAtLeastOneGroup() {
        List<List<Message>> groups = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            groups.add(List.of(new Message(Role.USER, "u" + i)));
        }
        int drop = MessageGroupDropper.ratioDropCount(groups.size());
        assertThat(drop).isGreaterThanOrEqualTo(1);
        assertThat(MessageGroupDropper.dropOldest(groups, drop).size()).isLessThan(groups.size());
    }

    @Test
    void flattenRestoresMessageList() {
        List<List<Message>> groups = List.of(
            List.of(new Message(Role.USER, "u1")),
            List.of(new Message(Role.USER, "u2"))
        );
        assertThat(MessageGroupDropper.flatten(groups)).hasSize(2);
    }
}
