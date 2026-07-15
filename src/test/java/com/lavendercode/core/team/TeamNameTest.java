package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TeamNameTest {
    @Test
    void sanitizesSpacesAndSymbols() {
        assertThat(TeamName.sanitize("refactor auth")).isEqualTo("refactor-auth");
        assertThat(TeamName.sanitize("  a@b  ")).isEqualTo("a-b");
    }

    @Test
    void rejectsEmptyAfterSanitize() {
        assertThatThrownBy(() -> TeamName.sanitize("@@@"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("空");
    }

    @Test
    void uniqueSuffixAvoidsCollision() {
        assertThat(TeamName.ensureUnique("demo", Set.of("demo"))).isEqualTo("demo-2");
        assertThat(TeamName.ensureUnique("demo", Set.of("demo", "demo-2"))).isEqualTo("demo-3");
    }
}
