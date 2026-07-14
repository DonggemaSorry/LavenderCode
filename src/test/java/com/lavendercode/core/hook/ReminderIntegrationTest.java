package com.lavendercode.core.hook;

import com.lavendercode.core.prompt.ReminderInjector;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReminderIntegrationTest {
    @Test
    void hookRemindersAppendedAfterPlanReminder() {
        var queue = new HookReminderQueue();
        queue.add("用 zh-CN 回复");
        queue.add("记得写测试");
        var result = ReminderInjector.inject(1, true, queue);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("PLAN MODE");
        assertThat(result.get()).contains("用 zh-CN 回复");
        assertThat(result.get()).contains("记得写测试");
    }

    @Test
    void noHookRemindersReturnsPlanOnly() {
        var queue = new HookReminderQueue();
        var result = ReminderInjector.inject(1, true, queue);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("PLAN MODE");
    }

    @Test
    void hookRemindersWithoutPlanMode() {
        var queue = new HookReminderQueue();
        queue.add("hook reminder");
        var result = ReminderInjector.inject(1, false, queue);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("hook reminder");
    }

    @Test
    void emptyQueueNoPlanModeReturnsEmpty() {
        var queue = new HookReminderQueue();
        var result = ReminderInjector.inject(1, false, queue);
        assertThat(result).isEmpty();
    }
}
