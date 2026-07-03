package com.lavendercode.core.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReminderInjectorTest {
    @Test
    void firstRoundPlanModeReturnsFullReminder() {
        var r = ReminderInjector.inject(1, true);
        assertThat(r).isPresent();
        assertThat(r.get()).contains("PLAN MODE");
        assertThat(r.get()).contains("read-only");
    }

    @Test
    void fifthRoundPlanModeReturnsFullReminder() {
        var r = ReminderInjector.inject(5, true);
        assertThat(r).isPresent();
        assertThat(r.get()).contains("PLAN MODE");
    }

    @Test
    void tenthRoundPlanModeReturnsFullReminder() {
        var r = ReminderInjector.inject(10, true);
        assertThat(r).isPresent();
        assertThat(r.get()).contains("PLAN MODE");
    }

    @Test
    void otherRoundsReturnBriefReminder() {
        var r = ReminderInjector.inject(2, true);
        assertThat(r).isPresent();
        assertThat(r.get()).contains("Plan mode");
        assertThat(r.get()).doesNotContain("PLAN MODE");
    }

    @Test
    void nonPlanModeReturnsEmpty() {
        var r = ReminderInjector.inject(1, false);
        assertThat(r).isEmpty();
    }

    @Test
    void reminderWrappedInSystemReminderTags() {
        var r = ReminderInjector.inject(1, true);
        assertThat(r.get()).contains("<system-reminder>");
        assertThat(r.get()).contains("</system-reminder>");
    }

    @Test
    void fullReminderContainsReadOnlyConstraint() {
        var r = ReminderInjector.inject(1, true);
        assertThat(r.get()).contains("read-only tools");
        assertThat(r.get()).contains("DO NOT");
    }
}
