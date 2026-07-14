package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class HookEngineImplTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // ── helpers ─────────────────────────────────────────────────────────────

    private HookConfig config(HookRule... rules) {
        return new HookConfig(List.of(rules), List.of());
    }

    private HookRule rule(String name, HookEvent event, HookAction action) {
        return new HookRule(name, event, null, action, false, false, TIMEOUT);
    }

    private HookRule ruleOnce(String name, HookEvent event, HookAction action) {
        return new HookRule(name, event, null, action, true, false, TIMEOUT);
    }

    private HookRule ruleWithCondition(String name, HookEvent event,
                                        HookCondition condition, HookAction action) {
        return new HookRule(name, event, condition, action, false, false, TIMEOUT);
    }

    private HookPayload payload(HookEvent event) {
        return HookPayload.builder(event).sessionId("sess-1").build();
    }

    // ── 1. no rules → allowed ───────────────────────────────────────────────

    @Test
    void noRulesReturnsAllowed() {
        var engine = new HookEngineImpl(config());
        var result = engine.dispatch(HookEvent.SessionStart, payload(HookEvent.SessionStart),
                new AtomicBoolean());
        assertThat(result.blocked()).isFalse();
    }

    // ── 2. unconditional prompt hook adds reminder ──────────────────────────

    @Test
    void unconditionalPromptHookAddsReminder() {
        var engine = new HookEngineImpl(config(
                rule("greet", HookEvent.SessionStart, new HookAction.Prompt("Welcome!"))
        ));
        engine.dispatch(HookEvent.SessionStart, payload(HookEvent.SessionStart), new AtomicBoolean());
        assertThat(engine.reminderQueue().drain()).containsExactly("Welcome!");
    }

    // ── 3. conditional hook skips on mismatch ───────────────────────────────

    @Test
    void conditionalHookSkipsOnMismatch() {
        var condition = new HookCondition.AllOf(List.of(
                new HookCondition.Atom("tool_name",
                        new com.lavendercode.core.permission.MatchType.Exact("Bash"))
        ));
        var engine = new HookEngineImpl(config(
                ruleWithCondition("bash-only", HookEvent.PreToolUse, condition,
                        new HookAction.Prompt("Bash detected"))
        ));
        var p = HookPayload.builder(HookEvent.PreToolUse).put("tool_name", "Read").build();
        engine.dispatch(HookEvent.PreToolUse, p, new AtomicBoolean());
        assertThat(engine.reminderQueue().isEmpty()).isTrue();
    }

    // ── 4. only_once executes once ─────────────────────────────────────────

    @Test
    void onlyOnceExecutesOnce() {
        var engine = new HookEngineImpl(config(
                ruleOnce("once-greet", HookEvent.SessionStart, new HookAction.Prompt("Hi"))
        ));
        var p = payload(HookEvent.SessionStart);
        engine.dispatch(HookEvent.SessionStart, p, new AtomicBoolean());
        engine.dispatch(HookEvent.SessionStart, p, new AtomicBoolean());
        assertThat(engine.reminderQueue().drain()).containsExactly("Hi");
    }

    // ── 5. clearOnce allows re-execution ────────────────────────────────────

    @Test
    void clearOnceOnSessionReset() {
        var engine = new HookEngineImpl(config(
                ruleOnce("once-greet", HookEvent.SessionStart, new HookAction.Prompt("Hi"))
        ));
        var p = payload(HookEvent.SessionStart);
        engine.dispatch(HookEvent.SessionStart, p, new AtomicBoolean());
        engine.clearOnce();
        engine.dispatch(HookEvent.SessionStart, p, new AtomicBoolean());
        assertThat(engine.reminderQueue().drain()).containsExactly("Hi", "Hi");
    }

    // ── 6. shell exit 2 blocks PreToolUse ───────────────────────────────────

    @Test
    void shellExit2BlocksPreToolUse() {
        // cmd /c on Windows, sh -c on Unix
        String cmd = isWindows()
                ? "echo blocked >&2 & exit 2"
                : "echo blocked >&2; exit 2";
        var engine = new HookEngineImpl(config(
                rule("block-tool", HookEvent.PreToolUse, new HookAction.Shell(cmd))
        ));
        var p = HookPayload.builder(HookEvent.PreToolUse).put("tool_name", "Bash").build();
        var result = engine.dispatch(HookEvent.PreToolUse, p, new AtomicBoolean());
        assertThat(result.blocked()).isTrue();
        assertThat(result.reason()).containsIgnoringCase("blocked");
        assertThat(result.hookName()).isEqualTo("block-tool");
    }

    // ── 7. shell exit 0 allows PreToolUse ───────────────────────────────────

    @Test
    void shellExit0AllowsPreToolUse() {
        String cmd = isWindows() ? "exit 0" : "exit 0";
        var engine = new HookEngineImpl(config(
                rule("allow-tool", HookEvent.PreToolUse, new HookAction.Shell(cmd))
        ));
        var p = HookPayload.builder(HookEvent.PreToolUse).put("tool_name", "Read").build();
        var result = engine.dispatch(HookEvent.PreToolUse, p, new AtomicBoolean());
        assertThat(result.blocked()).isFalse();
    }

    // ── 8. shell failure (exit 1) does not block ────────────────────────────

    @Test
    void shellFailureDoesNotBlock() {
        String cmd = isWindows() ? "exit 1" : "exit 1";
        var engine = new HookEngineImpl(config(
                rule("fail-tool", HookEvent.PreToolUse, new HookAction.Shell(cmd))
        ));
        var p = HookPayload.builder(HookEvent.PreToolUse).put("tool_name", "Read").build();
        var result = engine.dispatch(HookEvent.PreToolUse, p, new AtomicBoolean());
        assertThat(result.blocked()).isFalse();
    }

    // ── 9. subagent logs placeholder ────────────────────────────────────────

    @Test
    void subagentLogsPlaceholder() {
        var engine = new HookEngineImpl(config(
                rule("sub-hook", HookEvent.SessionStart,
                        new HookAction.Subagent("coder", "do something"))
        ));

        // capture stderr
        var origErr = System.err;
        var buf = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buf));
        try {
            engine.dispatch(HookEvent.SessionStart, payload(HookEvent.SessionStart),
                    new AtomicBoolean());
        } finally {
            System.setErr(origErr);
        }

        String errOut = buf.toString();
        assertThat(errOut).contains("[hook subagent] not yet implemented, skipped: sub-hook");
        assertThat(engine.reminderQueue().isEmpty()).isTrue();
    }

    // ── utility ─────────────────────────────────────────────────────────────

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
