package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternMatcherTest {

    @Test
    void exactMatchWholeString() {
        var m = new PatternMatcher(new MatchType.Exact("git status"));
        assertThat(m.matches("git status")).isTrue();
        assertThat(m.matches("git status -s")).isFalse();
    }

    @Test
    void globMatchesWildcard() {
        var m = new PatternMatcher(new MatchType.Glob("git *"));
        assertThat(m.matches("git status")).isTrue();
        assertThat(m.matches("git log --oneline")).isTrue();
        assertThat(m.matches("npm install")).isFalse();
    }

    @Test
    void regexMatchPattern() {
        var m = new PatternMatcher(new MatchType.Regex("^npm (install|test)$"));
        assertThat(m.matches("npm install")).isTrue();
        assertThat(m.matches("npm test")).isTrue();
        assertThat(m.matches("npm run dev")).isFalse();
    }

    @Test
    void notWrapsAndInverts() {
        var inner = new MatchType.Regex("^rm");
        var m = new PatternMatcher(new MatchType.Not(inner));
        assertThat(m.matches("rm -rf .")).isFalse();
        assertThat(m.matches("ls -lh")).isTrue();
    }

    @Test
    void notExactNesting() {
        var m = new PatternMatcher(new MatchType.Not(new MatchType.Exact("Bash")));
        assertThat(m.matches("Bash")).isFalse();
        assertThat(m.matches("Write")).isTrue();
    }

    @Test
    void regexCompileFailsThrows() {
        assertThatThrownBy(() -> new PatternMatcher(new MatchType.Regex("[invalid")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyGlobMatchesAll() {
        var m = new PatternMatcher(new MatchType.Glob(""));
        assertThat(m.matches("anything")).isTrue();
    }
}
