package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class ReActLoopHookIntegrationTest {
    @Test
    void preUserMessageHookInjectsReminder() {
        var rule = new HookRule("rem", HookEvent.PreUserMessage, null,
            new HookAction.Prompt("be concise"), true, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var payload = HookPayload.builder(HookEvent.PreUserMessage)
            .sessionId("s").cwd(java.nio.file.Path.of("/tmp")).mode("default")
            .put("prompt", "hello").build();
        engine.dispatch(HookEvent.PreUserMessage, payload, new AtomicBoolean(false));
        assertThat(engine.reminderQueue().drain()).containsExactly("be concise");
    }

    @Test
    void stopHookFires() {
        var rule = new HookRule("s", HookEvent.Stop, null,
            new HookAction.Prompt("done"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var payload = HookPayload.builder(HookEvent.Stop)
            .sessionId("s").cwd(java.nio.file.Path.of("/tmp")).mode("default")
            .put("iter", 5).build();
        engine.dispatch(HookEvent.Stop, payload, new AtomicBoolean(false));
        assertThat(engine.reminderQueue().drain()).containsExactly("done");
    }
}
