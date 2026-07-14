package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PatternMatcherParseTest {

    @Test
    void parseBareStringIsGlob() {
        var m = PatternMatcher.parse("git *");
        assertThat(m.type()).isInstanceOf(MatchType.Glob.class);
        assertThat(m.matches("git status")).isTrue();
    }

    @Test
    void parseEqualsPrefixIsExact() {
        var m = PatternMatcher.parse("=git status");
        assertThat(m.type()).isInstanceOf(MatchType.Exact.class);
        assertThat(m.matches("git status")).isTrue();
        assertThat(m.matches("git status -s")).isFalse();
    }

    @Test
    void parseTildePrefixIsRegex() {
        var m = PatternMatcher.parse("~^npm (install|test)$");
        assertThat(m.type()).isInstanceOf(MatchType.Regex.class);
        assertThat(m.matches("npm install")).isTrue();
    }

    @Test
    void parseBangPrefixIsNot() {
        var m = PatternMatcher.parse("!~^rm");
        assertThat(m.type()).isInstanceOf(MatchType.Not.class);
        assertThat(m.matches("rm -rf .")).isFalse();
        assertThat(m.matches("ls -lh")).isTrue();
    }

    @Test
    void parseNotExact() {
        var m = PatternMatcher.parse("!=value");
        assertThat(m.type()).isInstanceOf(MatchType.Not.class);
        assertThat(m.matches("value")).isFalse();
        assertThat(m.matches("other")).isTrue();
    }

    @Test
    void parseEmptyStringIsEmptyGlob() {
        var m = PatternMatcher.parse("");
        assertThat(m.matches("anything")).isTrue();
    }
}
