package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubstituteArgumentsTest {

    @Test
    void nullArgsReturnsBodyAsIs() {
        assertThat(SkillExecutor.substituteArguments("body", null)).isEqualTo("body");
    }

    @Test
    void blankArgsReturnsBodyAsIs() {
        assertThat(SkillExecutor.substituteArguments("body", "  ")).isEqualTo("body");
    }

    @Test
    void replacesArgumentsPlaceholder() {
        String body = "Review this code: $ARGUMENTS\nThank you.";
        assertThat(SkillExecutor.substituteArguments(body, "src/main.java"))
            .isEqualTo("Review this code: src/main.java\nThank you.");
    }

    @Test
    void appendsUserRequestWhenNoPlaceholder() {
        String body = "You are a code reviewer.";
        String result = SkillExecutor.substituteArguments(body, "check this file");
        assertThat(result).isEqualTo("You are a code reviewer.\n\n## User Request\ncheck this file");
    }

    @Test
    void replacesMultipleOccurrences() {
        String body = "Arg1: $ARGUMENTS\nArg2: $ARGUMENTS";
        assertThat(SkillExecutor.substituteArguments(body, "test"))
            .isEqualTo("Arg1: test\nArg2: test");
    }
}
