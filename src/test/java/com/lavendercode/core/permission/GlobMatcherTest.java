package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlobMatcherTest {
    @Test
    void starMatchesAnySegment() {
        assertThat(GlobMatcher.matches("git status", "git *", false)).isTrue();
        assertThat(GlobMatcher.matches("git push", "git *", false)).isTrue();
        assertThat(GlobMatcher.matches("npm install", "git *", false)).isFalse();
    }

    @Test
    void doubleStarOnlyForPaths() {
        assertThat(GlobMatcher.matches("src/foo/bar.java", "src/**", true)).isTrue();
        assertThat(GlobMatcher.matches("docs/x", "src/**", true)).isFalse();
    }

    @Test
    void exactMatch() {
        assertThat(GlobMatcher.matches("git status", "git status", false)).isTrue();
        assertThat(GlobMatcher.matches("git push", "git status", false)).isFalse();
    }

    @Test
    void emptyPatternMatchesAll() {
        assertThat(GlobMatcher.matches("anything", "", false)).isTrue();
    }
}
