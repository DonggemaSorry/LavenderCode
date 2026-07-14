package com.lavendercode.core.hook;

import com.lavendercode.core.permission.MatchType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HookConfigLoaderTest {

    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream errCapture;

    @BeforeEach
    void setUp() {
        errCapture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errCapture));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
    }

    private String stderr() {
        return errCapture.toString();
    }

    // ── 1. noConfigReturnsEmpty ─────────────────────────────────────────────

    @Test
    void noConfigReturnsEmpty(@TempDir Path tmp) {
        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).isEmpty();
    }

    // ── 2. singleProjectHook ────────────────────────────────────────────────

    @Test
    void singleProjectHook(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: test-hook
                    event: PostToolUse
                    action:
                      type: shell
                      command: "echo hi"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).hasSize(1);
        HookRule rule = cfg.rules().get(0);
        assertThat(rule.name()).isEqualTo("test-hook");
        assertThat(rule.event()).isEqualTo(HookEvent.PostToolUse);
        assertThat(rule.action()).isInstanceOf(HookAction.Shell.class);
        assertThat(((HookAction.Shell) rule.action()).command()).isEqualTo("echo hi");
    }

    // ── 3. twoLayerMerge ────────────────────────────────────────────────────

    @Test
    void twoLayerMerge(@TempDir Path projectDir, @TempDir Path userDir) throws Exception {
        // project-level
        Path projHooksDir = projectDir.resolve(".lavendercode");
        Files.createDirectories(projHooksDir);
        Files.writeString(projHooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: project-hook
                    event: SessionStart
                    action:
                      type: shell
                      command: "echo project"
                """);

        // user-level
        Path userHooksDir = userDir.resolve(".lavendercode");
        Files.createDirectories(userHooksDir);
        Files.writeString(userHooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: user-hook
                    event: SessionEnd
                    action:
                      type: shell
                      command: "echo user"
                """);

        HookConfig cfg = new HookConfigLoader().load(projectDir, userDir);
        assertThat(cfg.rules()).hasSize(2);
        assertThat(cfg.rules().stream().map(HookRule::name).toList())
                .containsExactly("project-hook", "user-hook");
        assertThat(cfg.sources()).hasSize(2);
    }

    // ── 4. duplicateNameSkipsLater ──────────────────────────────────────────

    @Test
    void duplicateNameSkipsLater(@TempDir Path projectDir, @TempDir Path userDir) throws Exception {
        // project-level
        Path projHooksDir = projectDir.resolve(".lavendercode");
        Files.createDirectories(projHooksDir);
        Files.writeString(projHooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: same-name
                    event: SessionStart
                    action:
                      type: shell
                      command: "echo project"
                """);

        // user-level (same name → should be skipped, project wins)
        Path userHooksDir = userDir.resolve(".lavendercode");
        Files.createDirectories(userHooksDir);
        Files.writeString(userHooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: same-name
                    event: SessionEnd
                    action:
                      type: shell
                      command: "echo user"
                """);

        HookConfig cfg = new HookConfigLoader().load(projectDir, userDir);
        assertThat(cfg.rules()).hasSize(1);
        // project wins
        assertThat(cfg.rules().get(0).event()).isEqualTo(HookEvent.SessionStart);
        assertThat(stderr()).containsIgnoringCase("duplicate");
    }

    // ── 5. unknownEventSkipped ──────────────────────────────────────────────

    @Test
    void unknownEventSkipped(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: bad-event
                    event: UnknownEvent
                    action:
                      type: shell
                      command: "echo"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).isEmpty();
        assertThat(stderr()).containsIgnoringCase("unknown event");
    }

    // ── 6. asyncOnBlockingEventSkipped ───────────────────────────────────────

    @Test
    void asyncOnBlockingEventSkipped(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: bad-async
                    event: PreToolUse
                    async: true
                    action:
                      type: shell
                      command: "echo"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).isEmpty();
        assertThat(stderr()).containsIgnoringCase("async");
    }

    // ── 7. allOfAndAnyOfBothPresentSkipped ───────────────────────────────────

    @Test
    void allOfAndAnyOfBothPresentSkipped(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: bad-condition
                    event: PostToolUse
                    if:
                      all_of:
                        - field: tool_name
                          match: { type: exact, value: write_file }
                      any_of:
                        - field: tool_name
                          match: { type: exact, value: read_file }
                    action:
                      type: shell
                      command: "echo"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).isEmpty();
        assertThat(stderr()).containsIgnoringCase("all_of");
    }

    // ── 8. invalidYamlDoesNotCrash ──────────────────────────────────────────

    @Test
    void invalidYamlDoesNotCrash(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"),
                "{{not valid yaml :: [\n  broken");

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).isEmpty();
        assertThat(stderr()).containsIgnoringCase("invalid YAML");
    }

    // ── 9. timeoutParsed ────────────────────────────────────────────────────

    @Test
    void timeoutParsed(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: timeout-hook
                    event: PostToolUse
                    timeout: 5s
                    action:
                      type: shell
                      command: "echo"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).hasSize(1);
        assertThat(cfg.rules().get(0).timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    // ── 10. conditionParsed ─────────────────────────────────────────────────

    @Test
    void conditionParsed(@TempDir Path tmp) throws Exception {
        Path hooksDir = tmp.resolve(".lavendercode");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("hooks.yaml"), """
                hooks:
                  - name: cond-hook
                    event: PostToolUse
                    if:
                      all_of:
                        - field: tool_name
                          match: { type: exact, value: write_file }
                    action:
                      type: shell
                      command: "echo"
                """);

        HookConfig cfg = new HookConfigLoader().load(tmp, tmp);
        assertThat(cfg.rules()).hasSize(1);

        HookRule rule = cfg.rules().get(0);
        assertThat(rule.condition()).isInstanceOf(HookCondition.AllOf.class);

        HookCondition.AllOf allOf = (HookCondition.AllOf) rule.condition();
        assertThat(allOf.atoms()).hasSize(1);

        HookCondition.Atom atom = allOf.atoms().get(0);
        assertThat(atom.field()).isEqualTo("tool_name");
        assertThat(atom.match()).isInstanceOf(MatchType.Exact.class);
        assertThat(((MatchType.Exact) atom.match()).value()).isEqualTo("write_file");
    }
}
