package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionPipelineTest {
    @TempDir Path root;

    @Test
    void blacklistShortCircuitsBeforeSandbox() {
        var mode = new AtomicReference<>(PermissionMode.BYPASS_PERMISSIONS);
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), mode::get, (req, f) -> HitlChoice.ALLOW_ONCE, root, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "rm -rf /")), root);
        var outcome = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(outcome.denied()).isTrue();
        assertThat(outcome.deny().source()).isEqualTo("BLACKLIST");
    }

    @Test
    void allowRuleSkipsModeFallback() {
        var rules = List.of(PermissionRule.parse("Read(*)", PermissionRule.Effect.ALLOW));
        var cfg = new PermissionConfig(rules, PermissionMode.DEFAULT);
        var pipeline = PermissionPipeline.create(cfg, () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.DENY, root, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "x")), root);
        assertThat(pipeline.evaluate(ctx, new AtomicBoolean(false)).allowed()).isTrue();
    }

    @Test
    void askDelegatesToHitlGate() {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.DENY, root, r -> {});
        var ctx = ToolMetadata.from(
            new ToolCall("1", "write_file", Map.of("path", "a.txt", "content", "x")), root);
        var outcome = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(outcome.denied()).isTrue();
        assertThat(outcome.deny().source()).isEqualTo("USER");
    }

    @Test
    void parseFailedDeniesAtStart() {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.BYPASS_PERMISSIONS,
            (req, f) -> HitlChoice.ALLOW_ONCE, root, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "read_file", Map.of(), "bad json"), root);
        var outcome = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(outcome.denied()).isTrue();
        assertThat(outcome.deny().source()).isEqualTo("PARSE");
    }

    @Test
    void allowPermanentWritesRuleAndReloads() throws Exception {
        var reloaded = new AtomicReference<List<PermissionRule>>();
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.ALLOW_PERMANENT, root, reloaded::set);
        var ctx = ToolMetadata.from(
            new ToolCall("1", "write_file", Map.of("path", "src/Foo.java", "content", "x")), root);
        var outcome = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(outcome.allowed()).isTrue();
        assertThat(reloaded.get()).isNotNull();
        assertThat(reloaded.get()).anyMatch(r ->
            r.toolName().equals("Write") && r.patternMatcher().type() instanceof MatchType.Glob g && g.value().equals("src/Foo.java"));
    }
}
