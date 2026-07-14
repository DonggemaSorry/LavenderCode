package com.lavendercode.core.hook;

import com.lavendercode.core.permission.MatchType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionMatcherTest {

    private HookPayload payload() {
        return new HookPayload(HookEvent.PreToolUse, Map.of(
            "tool_name", "write_file",
            "tool_input", Map.of("path", "src/Main.java", "command", "echo hi")
        ));
    }

    @Test
    void nullConditionIsUnconditional() {
        assertThat(ConditionMatcher.matches(null, payload())).isTrue();
    }

    @Test
    void allOfAllPass() {
        var cond = new HookCondition.AllOf(List.of(
            new HookCondition.Atom("tool_name", new MatchType.Exact("write_file")),
            new HookCondition.Atom("tool_input.path", new MatchType.Glob("**/*.java"))
        ));
        assertThat(ConditionMatcher.matches(cond, payload())).isTrue();
    }

    @Test
    void allOfOneFails() {
        var cond = new HookCondition.AllOf(List.of(
            new HookCondition.Atom("tool_name", new MatchType.Exact("write_file")),
            new HookCondition.Atom("tool_input.path", new MatchType.Glob("**/*.py"))
        ));
        assertThat(ConditionMatcher.matches(cond, payload())).isFalse();
    }

    @Test
    void anyOfOnePass() {
        var cond = new HookCondition.AnyOf(List.of(
            new HookCondition.Atom("tool_name", new MatchType.Exact("read_file")),
            new HookCondition.Atom("tool_name", new MatchType.Exact("write_file"))
        ));
        assertThat(ConditionMatcher.matches(cond, payload())).isTrue();
    }

    @Test
    void anyOfNonePass() {
        var cond = new HookCondition.AnyOf(List.of(
            new HookCondition.Atom("tool_name", new MatchType.Exact("read_file")),
            new HookCondition.Atom("tool_name", new MatchType.Exact("grep"))
        ));
        assertThat(ConditionMatcher.matches(cond, payload())).isFalse();
    }

    @Test
    void nestedFieldExtraction() {
        var val = ConditionMatcher.extractField("tool_input.command", payload());
        assertThat(val).isEqualTo("echo hi");
    }

    @Test
    void missingFieldReturnsEmptyString() {
        var val = ConditionMatcher.extractField("tool_input.nonexistent", payload());
        assertThat(val).isEqualTo("");
    }

    @Test
    void notMatchInverts() {
        var cond = new HookCondition.AllOf(List.of(
            new HookCondition.Atom("tool_name", new MatchType.Not(new MatchType.Exact("read_file")))
        ));
        assertThat(ConditionMatcher.matches(cond, payload())).isTrue();
    }
}
