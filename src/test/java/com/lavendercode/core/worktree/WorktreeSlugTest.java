package com.lavendercode.core.worktree;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorktreeSlugTest {

    @Test
    void acceptsNestedSlug() {
        assertThatCode(() -> WorktreeSlug.validate("feature/a")).doesNotThrowAnyException();
        assertThat(WorktreeSlug.flatten("team/alice")).isEqualTo("team+alice");
    }

    @Test
    void rejectsPathTraversalAndBadForms() {
        assertThatThrownBy(() -> WorktreeSlug.validate("../etc"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorktreeSlug.validate(".."))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorktreeSlug.validate("a//b"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorktreeSlug.validate("a/b "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorktreeSlug.validate(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
