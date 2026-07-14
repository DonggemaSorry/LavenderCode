package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class EndToEndHookTest {

    @Test
    void userPromptSubmitShellExit2Blocks() {
        var rule = new HookRule("block-delete", HookEvent.UserPromptSubmit, null,
            new HookAction.Shell("echo blocked >&2 & exit 2"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var payload = HookPayload.builder(HookEvent.UserPromptSubmit)
            .sessionId("s").cwd(Path.of("/tmp")).mode("default")
            .put("prompt", "请帮我 delete 那个文件").build();
        var result = engine.dispatch(HookEvent.UserPromptSubmit, payload, new AtomicBoolean(false));
        assertThat(result.blocked()).isTrue();
    }

    @Test
    void userPromptSubmitExit0Allows() {
        var rule = new HookRule("allow", HookEvent.UserPromptSubmit, null,
            new HookAction.Shell("exit 0"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var payload = HookPayload.builder(HookEvent.UserPromptSubmit)
            .sessionId("s").cwd(Path.of("/tmp")).mode("default")
            .put("prompt", "hello").build();
        var result = engine.dispatch(HookEvent.UserPromptSubmit, payload, new AtomicBoolean(false));
        assertThat(result.blocked()).isFalse();
    }

    @Test
    void notificationEventFires() {
        var rule = new HookRule("notify", HookEvent.Notification, null,
            new HookAction.Prompt("notification seen"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var payload = HookPayload.builder(HookEvent.Notification)
            .sessionId("s").cwd(Path.of("/tmp")).mode("default")
            .put("kind", "approval").put("detail", "Bash").build();
        engine.dispatch(HookEvent.Notification, payload, new AtomicBoolean(false));
        assertThat(engine.reminderQueue().drain()).containsExactly("notification seen");
    }
}
