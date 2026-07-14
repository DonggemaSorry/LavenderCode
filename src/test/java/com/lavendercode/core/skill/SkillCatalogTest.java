package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SkillCatalogTest {

    private SkillCatalog.SkillMeta meta(String name) {
        return SkillCatalog.SkillMeta.withDefaults(
            name, "desc", null, List.of(), null, "inline", null, null);
    }

    private SkillCatalog.Skill skill(String name) {
        return new SkillCatalog.Skill(meta(name), "body", Path.of("/tmp"), true);
    }

    @Test
    void registerAndGetByName() {
        var catalog = new SkillCatalog();
        catalog.register(skill("commit"));
        assertThat(catalog.get("commit")).isNotNull();
        assertThat(catalog.get("commit").meta().name()).isEqualTo("commit");
    }

    @Test
    void registerOverwritesSameName() {
        var catalog = new SkillCatalog();
        catalog.register(skill("commit"));
        catalog.register(new SkillCatalog.Skill(
            meta("commit"), "new body", Path.of("/tmp"), true));
        assertThat(catalog.get("commit").promptBody()).isEqualTo("new body");
    }

    @Test
    void getReturnsNullForUnknown() {
        var catalog = new SkillCatalog();
        assertThat(catalog.get("unknown")).isNull();
    }

    @Test
    void listReturnsAllMetasInOrder() {
        var catalog = new SkillCatalog();
        catalog.register(skill("zebra"));
        catalog.register(skill("alpha"));
        var metas = catalog.list();
        assertThat(metas).extracting(SkillCatalog.SkillMeta::name)
            .containsExactly("zebra", "alpha");
    }

    @Test
    void sourceReturnsSourceDir() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            meta("test"), "body", Path.of("/skills/test"), true));
        assertThat(catalog.source("test")).isEqualTo(Path.of("/skills/test"));
    }

    @Test
    void sourceReturnsNullForUnknown() {
        var catalog = new SkillCatalog();
        assertThat(catalog.source("unknown")).isNull();
    }

    @Test
    void reloadClearsAll() {
        var catalog = new SkillCatalog();
        catalog.register(skill("a"));
        catalog.register(skill("b"));
        catalog.reload();
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void buildActiveContextIncludesAllSkills() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults("commit", "提交代码变更", "需要提交时", List.of(), null, "inline", null, null),
            "body", Path.of("/tmp"), true));
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults("review", "审查代码", null, List.of(), null, "inline", null, null),
            "body", Path.of("/tmp"), true));
        String ctx = catalog.buildActiveContext();
        assertThat(ctx).contains("commit");
        assertThat(ctx).contains("提交代码变更");
        assertThat(ctx).contains("review");
        assertThat(ctx).contains("审查代码");
    }

    @Test
    void buildActiveContextEmptyWhenNoSkills() {
        var catalog = new SkillCatalog();
        assertThat(catalog.buildActiveContext()).isEmpty();
    }

    // --- loadFromDirectory tests (added in Task 5) ---
    // --- loadCatalog tests (added in Task 6) ---

    @TempDir
    Path tempDir;
}
