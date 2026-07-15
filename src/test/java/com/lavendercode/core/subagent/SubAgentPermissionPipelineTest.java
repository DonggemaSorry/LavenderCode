package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.*;
import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class SubAgentPermissionPipelineTest {

    @TempDir Path root;

    @Test
    void dontAskSkipsHitlForCommand() {
        AtomicBoolean hitlCalled = new AtomicBoolean(false);
        HitlGate gate = (req, flag) -> {
            hitlCalled.set(true);
            return HitlChoice.DENY;
        };
        var pipeline = SubAgentPermissionPipeline.create(
            RuleEngineLayer.ofRules(List.of()), PermissionMode.DONT_ASK,
            gate, root, "explore", r -> {});
        var ctx = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "ls")), root);
        PermissionOutcome out = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(out.denied()).isFalse();
        assertThat(hitlCalled).isFalse();
    }

    @Test
    void defaultModeDelegatesToHitlWithPrefix() {
        AtomicBoolean hitlCalled = new AtomicBoolean(false);
        String[] capturedDetail = new String[1];
        HitlGate gate = (req, flag) -> {
            hitlCalled.set(true);
            capturedDetail[0] = req.detail();
            return HitlChoice.DENY;
        };
        var pipeline = SubAgentPermissionPipeline.create(
            RuleEngineLayer.ofRules(List.of()), PermissionMode.DEFAULT,
            gate, root, "explore", r -> {});
        var ctx = ToolMetadata.from(
            new ToolCall("1", "write_file", Map.of("path", "a.txt", "content", "x")), root);
        PermissionOutcome out = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(out.denied()).isTrue();
        assertThat(hitlCalled).isTrue();
        assertThat(capturedDetail[0]).contains("[来自 SubAgent explore]");
    }

    @Test
    void parentAllowSkipsHitl() {
        AtomicBoolean hitlCalled = new AtomicBoolean(false);
        HitlGate gate = (req, flag) -> {
            hitlCalled.set(true);
            return HitlChoice.DENY;
        };
        var parentRules = List.of(
            PermissionRule.parse("Write(src/Foo.java)", PermissionRule.Effect.ALLOW));
        var pipeline = SubAgentPermissionPipeline.create(
            RuleEngineLayer.ofRules(parentRules), PermissionMode.DEFAULT,
            gate, root, "explore", r -> {});
        var ctx = ToolMetadata.from(
            new ToolCall("1", "write_file", Map.of("path", "src/Foo.java", "content", "x")), root);
        PermissionOutcome out = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(out.allowed()).isTrue();
        assertThat(hitlCalled).isFalse();
    }
}
