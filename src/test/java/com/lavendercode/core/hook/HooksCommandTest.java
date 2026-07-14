package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class HooksCommandTest {

    @Test
    void noHooksOutputsNoHooksLoaded() {
        var config = new HookConfig(List.of(), List.of());
        String output = formatHooks(config);
        assertThat(output).isEqualTo("No hooks loaded.");
    }

    @Test
    void hooksListedByEvent() {
        var rules = List.of(
            new HookRule("h1", HookEvent.PreToolUse, null,
                new HookAction.Shell("echo x"), true, false, Duration.ofSeconds(30)),
            new HookRule("h2", HookEvent.Stop, null,
                new HookAction.Http("http://x", "POST", Map.of(), null), false, true, Duration.ofSeconds(10))
        );
        var config = new HookConfig(rules, List.of("/tmp/.lavendercode/hooks.yaml"));
        String output = formatHooks(config);
        assertThat(output).contains("h1");
        assertThat(output).contains("h2");
        assertThat(output).contains("PreToolUse");
        assertThat(output).contains("Stop");
        assertThat(output).contains("[once]");
        assertThat(output).contains("[async]");
        assertThat(output).contains("Loaded from:");
    }

    @Test
    void hookWithoutFlags() {
        var rules = List.of(
            new HookRule("simple", HookEvent.SessionStart, null,
                new HookAction.Prompt("hello"), false, false, Duration.ofSeconds(30))
        );
        var config = new HookConfig(rules, List.of("test"));
        String output = formatHooks(config);
        assertThat(output).contains("simple");
        assertThat(output).contains("SessionStart");
        assertThat(output).contains("prompt");
        assertThat(output).doesNotContain("[once]");
        assertThat(output).doesNotContain("[async]");
    }

    static String formatHooks(HookConfig config) {
        if (config.rules().isEmpty()) return "No hooks loaded.";
        var sb = new StringBuilder();
        for (var rule : config.rules()) {
            sb.append("  ").append(rule.name())
              .append("  ").append(rule.event())
              .append("  ").append(rule.action().getClass().getSimpleName().toLowerCase());
            var flags = new java.util.ArrayList<String>();
            if (rule.onlyOnce()) flags.add("[once]");
            if (rule.async()) flags.add("[async]");
            if (!flags.isEmpty()) sb.append("  ").append(String.join(" ", flags));
            sb.append('\n');
        }
        sb.append("Loaded from: ").append(String.join(", ", config.sources()));
        return sb.toString().stripTrailing();
    }
}
