package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class HookActionTest {
    @Test
    void shellActionHasCommand() {
        var a = new HookAction.Shell("echo hello");
        assertThat(a).isInstanceOf(HookAction.class);
        assertThat(a.command()).isEqualTo("echo hello");
    }

    @Test
    void promptActionHasText() {
        var a = new HookAction.Prompt("用 zh-CN 回复");
        assertThat(a.text()).isEqualTo("用 zh-CN 回复");
    }

    @Test
    void httpActionDefaults() {
        var a = new HookAction.Http("http://localhost:9999/done", "POST", Map.of(), null);
        assertThat(a.url()).isEqualTo("http://localhost:9999/done");
        assertThat(a.method()).isEqualTo("POST");
    }

    @Test
    void subagentActionHasFields() {
        var a = new HookAction.Subagent("foo", "do something");
        assertThat(a.agentName()).isEqualTo("foo");
    }
}
