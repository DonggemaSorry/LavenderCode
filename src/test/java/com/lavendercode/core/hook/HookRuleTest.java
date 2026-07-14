package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class HookRuleTest {
    @Test
    void ruleHasThreeElements() {
        var rule = new HookRule("test", HookEvent.SessionStart, null,
            new HookAction.Prompt("hello"), false, false, Duration.ofSeconds(30));
        assertThat(rule.name()).isEqualTo("test");
        assertThat(rule.event()).isEqualTo(HookEvent.SessionStart);
        assertThat(rule.condition()).isNull();
        assertThat(rule.action()).isInstanceOf(HookAction.Prompt.class);
    }

    @Test
    void ruleDefaults() {
        var rule = new HookRule("t", HookEvent.Stop, null,
            new HookAction.Shell("echo done"), false, false, Duration.ofSeconds(30));
        assertThat(rule.onlyOnce()).isFalse();
        assertThat(rule.async()).isFalse();
        assertThat(rule.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
