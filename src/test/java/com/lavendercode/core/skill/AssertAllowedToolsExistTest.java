package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.*;

class AssertAllowedToolsExistTest {

    private static class FakeHost implements SkillHost {
        final Predicate<String> hasToolFn;
        FakeHost(Predicate<String> hasToolFn) { this.hasToolFn = hasToolFn; }
        @Override public void activateSkill(String name, String body) {}
        @Override public void setToolFilter(Predicate<String> filter) {}
        @Override public boolean hasTool(String name) { return hasToolFn.test(name); }
    }

    @Test
    void nullAllowedToolsDoesNotThrow() {
        SkillExecutor.assertAllowedToolsExist(null, new FakeHost(n -> true));
    }

    @Test
    void emptyAllowedToolsDoesNotThrow() {
        SkillExecutor.assertAllowedToolsExist(List.of(), new FakeHost(n -> true));
    }

    @Test
    void allToolsExistDoesNotThrow() {
        var host = new FakeHost(n -> n.equals("read_file") || n.equals("grep"));
        SkillExecutor.assertAllowedToolsExist(List.of("read_file", "grep"), host);
    }

    @Test
    void missingToolThrowsIllegalState() {
        var host = new FakeHost(n -> n.equals("read_file"));
        assertThatThrownBy(() ->
            SkillExecutor.assertAllowedToolsExist(List.of("read_file", "write_file"), host))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("write_file");
    }
}
