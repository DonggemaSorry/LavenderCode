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

    // --- loadFromDirectory tests ---

    @Test
    void loadFromDirectoryLoadsYamlFormat() throws java.io.IOException {
        Path skillDir = tempDir.resolve("skills").resolve("commit");
        java.nio.file.Files.createDirectories(skillDir);
        java.nio.file.Files.writeString(skillDir.resolve("skill.yaml"),
            "name: commit\ndescription: 提交\n");
        java.nio.file.Files.writeString(skillDir.resolve("prompt.md"), "body");
        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));
        assertThat(catalog.get("commit")).isNotNull();
        assertThat(catalog.get("commit").meta().description()).isEqualTo("提交");
    }

    @Test
    void loadFromDirectoryLoadsSkillMdFormat() throws java.io.IOException {
        Path skillDir = tempDir.resolve("skills").resolve("review");
        java.nio.file.Files.createDirectories(skillDir);
        java.nio.file.Files.writeString(skillDir.resolve("SKILL.md"),
            "# Review\nDescription line.\nbody");
        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));
        assertThat(catalog.get("review")).isNotNull();
        assertThat(catalog.get("review").meta().description()).isEqualTo("Description line.");
    }

    @Test
    void loadFromDirectorySkipsParseFailures() throws java.io.IOException {
        Path ok = tempDir.resolve("skills").resolve("ok");
        java.nio.file.Files.createDirectories(ok);
        java.nio.file.Files.writeString(ok.resolve("SKILL.md"), "# OK\nbody");
        Path bad = tempDir.resolve("skills").resolve("bad");
        java.nio.file.Files.createDirectories(bad);
        java.nio.file.Files.writeString(bad.resolve("SKILL.md"), "");
        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));
        assertThat(catalog.get("ok")).isNotNull();
    }

    @Test
    void loadFromDirectoryNonExistentDoesNotThrow() {
        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void loadFromDirectoryYamlTakesPrecedence() throws java.io.IOException {
        Path skillDir = tempDir.resolve("skills").resolve("dual");
        java.nio.file.Files.createDirectories(skillDir);
        java.nio.file.Files.writeString(skillDir.resolve("skill.yaml"),
            "name: dual\ndescription: from yaml\n");
        java.nio.file.Files.writeString(skillDir.resolve("prompt.md"), "body");
        java.nio.file.Files.writeString(skillDir.resolve("SKILL.md"), "# dual\nfrom md");
        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));
        var skill = catalog.get("dual");
        assertThat(skill).isNotNull();
        assertThat(skill.promptBody()).isNull();
        assertThat(skill.bodyLoaded()).isFalse();
    }

    // --- loadCatalog tests (added in Task 6) ---

    @TempDir
    Path tempDir;
}
