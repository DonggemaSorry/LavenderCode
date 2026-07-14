package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionRuleExtendedTest {
    @TempDir Path root;

    @Test
    void existingGlobFormatStillWorks() {
        var rule = PermissionRule.parse("Bash(git *)", PermissionRule.Effect.ALLOW);
        var ctx = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "git status")), root);
        assertThat(rule.matches(ctx)).isTrue();
    }

    @Test
    void exactPrefixMatchesExactly() {
        var rule = PermissionRule.parse("Bash(=git status)", PermissionRule.Effect.ALLOW);
        var ctxMatch = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "git status")), root);
        var ctxNoMatch = ToolMetadata.from(
            new ToolCall("2", "execute_command", Map.of("command", "git status -s")), root);
        assertThat(rule.matches(ctxMatch)).isTrue();
        assertThat(rule.matches(ctxNoMatch)).isFalse();
    }

    @Test
    void regexPrefixMatchesPattern() {
        var rule = PermissionRule.parse("Bash(~^npm (install|test)$)", PermissionRule.Effect.DENY);
        var ctxMatch = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "npm install")), root);
        assertThat(rule.matches(ctxMatch)).isTrue();
    }

    @Test
    void notPrefixInvertsInner() {
        var rule = PermissionRule.parse("Bash(!~^rm)", PermissionRule.Effect.ALLOW);
        var ctxRm = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "rm -rf .")), root);
        var ctxLs = ToolMetadata.from(
            new ToolCall("2", "execute_command", Map.of("command", "ls -lh")), root);
        assertThat(rule.matches(ctxRm)).isFalse();
        assertThat(rule.matches(ctxLs)).isTrue();
    }

    @Test
    void invalidRegexThrowsOnParse() {
        assertThatThrownBy(() ->
            PermissionRule.parse("Bash(~[invalid)", PermissionRule.Effect.ALLOW))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
