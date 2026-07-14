package com.lavendercode.core.skill;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BuildForkSeedTest {

    private List<Message> messages(int count) {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Message(Role.USER, "msg-" + i));
        }
        return list;
    }

    @Test
    void fullReturnsAllMessages() {
        var parent = messages(10);
        var seed = SkillExecutor.buildForkSeed("full", parent);
        assertThat(seed).hasSize(10);
    }

    @Test
    void recentReturnsLast5Messages() {
        var parent = messages(10);
        var seed = SkillExecutor.buildForkSeed("recent", parent);
        assertThat(seed).hasSize(5);
        assertThat(seed.get(0).content()).isEqualTo("msg-5");
        assertThat(seed.get(4).content()).isEqualTo("msg-9");
    }

    @Test
    void recentReturnsAllWhenFewerThan5() {
        var parent = messages(3);
        var seed = SkillExecutor.buildForkSeed("recent", parent);
        assertThat(seed).hasSize(3);
    }

    @Test
    void noneReturnsEmptyList() {
        var parent = messages(10);
        var seed = SkillExecutor.buildForkSeed("none", parent);
        assertThat(seed).isEmpty();
    }

    @Test
    void nullParentReturnsEmpty() {
        var seed = SkillExecutor.buildForkSeed("full", null);
        assertThat(seed).isEmpty();
    }

    @Test
    void emptyParentReturnsEmpty() {
        var seed = SkillExecutor.buildForkSeed("full", List.of());
        assertThat(seed).isEmpty();
    }

    @Test
    void unknownContextReturnsEmpty() {
        var parent = messages(5);
        var seed = SkillExecutor.buildForkSeed("unknown", parent);
        assertThat(seed).isEmpty();
    }
}
