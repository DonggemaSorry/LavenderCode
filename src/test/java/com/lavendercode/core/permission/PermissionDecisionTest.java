package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionDecisionTest {
    @Test
    void allowDenyAskAreDistinct() {
        assertThat(new PermissionDecision.Allow()).isInstanceOf(PermissionDecision.class);
        assertThat(new PermissionDecision.Deny("BLACKLIST", "blocked", "fix").source()).isEqualTo("BLACKLIST");
        assertThat(new PermissionDecision.Ask("mode default").triggerReason()).contains("default");
    }

    @Test
    void outcomeWrapsDeny() {
        var deny = new PermissionDecision.Deny("SANDBOX", "越界", "用项目内路径");
        var outcome = PermissionOutcome.deny(deny);
        assertThat(outcome.denied()).isTrue();
        assertThat(outcome.deny().reason()).isEqualTo("越界");
    }

    @Test
    void permissionModeCyclesLabels() {
        assertThat(PermissionMode.DEFAULT.label()).isEqualTo("default");
        assertThat(PermissionMode.ACCEPT_EDITS.label()).isEqualTo("acceptEdits");
    }
}
