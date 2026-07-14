package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SkillMetaDefaultsTest {

    @Test
    void defaultsWhenFieldsMissing() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "My Skill", null, null, null, null, null, null, null);
        assertThat(meta.name()).isEqualTo("my-skill");
        assertThat(meta.description()).isNull();
        assertThat(meta.whenToUse()).isNull();
        assertThat(meta.tags()).isEmpty();
        assertThat(meta.allowedTools()).isNull();
        assertThat(meta.mode()).isEqualTo("inline");
        assertThat(meta.model()).isNull();
        assertThat(meta.forkContext()).isEqualTo("none");
    }

    @Test
    void nameDefaultFromDirectoryName() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "Code Review", null, null, null, null, null, null, null);
        assertThat(meta.name()).isEqualTo("code-review");
    }

    @Test
    void modeDefaultsToInline() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "test", "desc", null, List.of(), null, null, null, null);
        assertThat(meta.mode()).isEqualTo("inline");
    }

    @Test
    void contextForkBackwardCompatible() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "test", "desc", null, List.of(), null, "fork", null, null);
        assertThat(meta.mode()).isEqualTo("fork");
    }

    @Test
    void explicitValuesPreserved() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "test", "desc", "when", List.of("a"), List.of("read_file"),
            "fork", "claude-3", "full");
        assertThat(meta.name()).isEqualTo("test");
        assertThat(meta.mode()).isEqualTo("fork");
        assertThat(meta.model()).isEqualTo("claude-3");
        assertThat(meta.forkContext()).isEqualTo("full");
        assertThat(meta.allowedTools()).containsExactly("read_file");
    }

    @Test
    void skillWithBodyCreatesCopy() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "test", "desc", null, List.of(), null, "inline", null, null);
        var skill = new SkillCatalog.Skill(meta, null, Path.of("/tmp"), false);
        var withBody = skill.withBody("prompt text");
        assertThat(withBody.promptBody()).isEqualTo("prompt text");
        assertThat(withBody.bodyLoaded()).isTrue();
        assertThat(withBody.meta()).isEqualTo(meta);
        assertThat(withBody.sourceDir()).isEqualTo(skill.sourceDir());
    }
}
