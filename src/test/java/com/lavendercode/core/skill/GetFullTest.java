package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GetFullTest {

    @TempDir
    Path tempDir;

    @Test
    void getFullReloadsBodyForYamlFormat() throws IOException {
        Path skillDir = tempDir.resolve("skills").resolve("commit");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.yaml"),
            "name: commit\ndescription: test\n");
        Files.writeString(skillDir.resolve("prompt.md"), "Original prompt body.");

        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));

        var phase1 = catalog.get("commit");
        assertThat(phase1.promptBody()).isNull();
        assertThat(phase1.bodyLoaded()).isFalse();

        var full = catalog.getFull("commit");
        assertThat(full.promptBody()).isEqualTo("Original prompt body.");
        assertThat(full.bodyLoaded()).isTrue();

        // 缓存已更新
        var cached = catalog.get("commit");
        assertThat(cached.promptBody()).isEqualTo("Original prompt body.");
        assertThat(cached.bodyLoaded()).isTrue();
    }

    @Test
    void getFullReloadsBodyForSkillMdFormat() throws IOException {
        Path skillDir = tempDir.resolve("skills").resolve("review");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
            "---\nname: review\ndescription: test\n---\nReview body text.");

        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));

        var full = catalog.getFull("review");
        assertThat(full.promptBody()).contains("Review body text.");
        assertThat(full.bodyLoaded()).isTrue();
    }

    @Test
    void getFullReturnsNullForUnknown() {
        var catalog = new SkillCatalog();
        assertThat(catalog.getFull("unknown")).isNull();
    }

    @Test
    void getFullPreservesOldCacheWhenReadFails() throws IOException {
        Path skillDir = tempDir.resolve("skills").resolve("broken");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("skill.yaml"),
            "name: broken\ndescription: test\n");
        Files.writeString(skillDir.resolve("prompt.md"), "Original body.");

        var catalog = new SkillCatalog();
        catalog.loadFromDirectory(tempDir.resolve("skills"));
        catalog.getFull("broken"); // 加载到缓存

        // 删除 prompt.md 模拟读取失败
        Files.delete(skillDir.resolve("prompt.md"));

        var result = catalog.getFull("broken");
        assertThat(result).isNotNull();
        assertThat(result.promptBody()).isEqualTo("Original body.");
    }

    @Test
    void getFullReturnsCachedWhenSourceDirNull() {
        var meta = SkillCatalog.SkillMeta.withDefaults(
            "test", "desc", null, List.of(), null, "inline", null, null);
        var skill = new SkillCatalog.Skill(meta, "body", null, true);
        var catalog = new SkillCatalog();
        catalog.register(skill);
        var result = catalog.getFull("test");
        assertThat(result.promptBody()).isEqualTo("body");
    }
}
