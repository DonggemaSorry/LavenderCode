package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineLayerTest {
    @Test
    void denyWinsOverAllowInSameTier(@TempDir Path root) {
        var rules = List.of(
            PermissionRule.parse("Bash(git *)", PermissionRule.Effect.ALLOW),
            PermissionRule.parse("Bash(git push)", PermissionRule.Effect.DENY));
        var layer = RuleEngineLayer.ofRules(rules);
        var push = ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "git push")), root);
        assertThat(layer.evaluate(push)).isPresent().get().isInstanceOf(PermissionDecision.Deny.class);
    }

    @Test
    void localTierOverridesProject(@TempDir Path root) {
        var local = List.of(PermissionRule.parse("Bash(npm test)", PermissionRule.Effect.ALLOW));
        var project = List.of(PermissionRule.parse("Bash(npm *)", PermissionRule.Effect.DENY));
        var layer = RuleEngineLayer.fromTiers(local, project, List.of());
        var ctx = ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "npm test")), root);
        assertThat(layer.evaluate(ctx)).isPresent().get().isInstanceOf(PermissionDecision.Allow.class);
    }

    @Test
    void noMatchReturnsEmpty(@TempDir Path root) {
        var layer = RuleEngineLayer.ofRules(List.of(PermissionRule.parse("Bash(git *)", PermissionRule.Effect.ALLOW)));
        var ctx = ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "npm test")), root);
        assertThat(layer.evaluate(ctx)).isEmpty();
    }

    @Test
    void parseRuleExtractsToolAndPattern() {
        var rule = PermissionRule.parse("Write(src/**)", PermissionRule.Effect.ALLOW);
        assertThat(rule.toolName()).isEqualTo("Write");
        assertThat(rule.patternMatcher().type()).isInstanceOf(MatchType.Glob.class);
        assertThat(rule.effect()).isEqualTo(PermissionRule.Effect.ALLOW);
    }
}
