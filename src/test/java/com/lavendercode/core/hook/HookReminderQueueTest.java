package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class HookReminderQueueTest {
    @Test
    void emptyQueueReturnsEmpty() {
        var q = new HookReminderQueue();
        assertThat(q.drain()).isEmpty();
    }

    @Test
    void addAndDrainPreservesOrder() {
        var q = new HookReminderQueue();
        q.add("reminder1");
        q.add("reminder2");
        assertThat(q.drain()).containsExactly("reminder1", "reminder2");
    }

    @Test
    void drainClearsQueue() {
        var q = new HookReminderQueue();
        q.add("x");
        q.drain();
        assertThat(q.drain()).isEmpty();
    }

    @Test
    void interceptResult() {
        var r = HookInterceptResult.blocked("hook-name", "blocked reason");
        assertThat(r.blocked()).isTrue();
        assertThat(r.hookName()).isEqualTo("hook-name");
        assertThat(r.reason()).isEqualTo("blocked reason");
    }

    @Test
    void interceptResultAllowed() {
        var r = HookInterceptResult.allowed();
        assertThat(r.blocked()).isFalse();
    }
}
