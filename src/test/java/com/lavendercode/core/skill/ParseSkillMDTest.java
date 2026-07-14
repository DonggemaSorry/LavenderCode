package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class ParseSkillMDTest {

    @TempDir
    Path tempDir;

    private Path writeSkillMd(String content) throws IOException {
        Path dir = tempDir.resolve("my-skill");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
        return dir;
    }

    @Test
    void parseWithFrontmatter() throws IOException {
        Path dir = writeSkillMd("""
            ---
            name: code-review
            description: 审查代码变更
            whenToUse: 当用户请求代码审查时
            tags:
              - review
              - quality
            allowedTools:
              - read_file
              - grep
            mode: inline
            ---
            # Code Review Skill
            You are a code reviewer.
            """);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill).isNotNull();
        assertThat(skill.meta().name()).isEqualTo("code-review");
        assertThat(skill.meta().description()).isEqualTo("审查代码变更");
        assertThat(skill.meta().whenToUse()).isEqualTo("当用户请求代码审查时");
        assertThat(skill.meta().tags()).containsExactly("review", "quality");
        assertThat(skill.meta().allowedTools()).containsExactly("read_file", "grep");
        assertThat(skill.meta().mode()).isEqualTo("inline");
        assertThat(skill.promptBody()).contains("You are a code reviewer.");
        assertThat(skill.bodyLoaded()).isTrue();
    }

    @Test
    void parseWithoutFrontmatter() throws IOException {
        Path dir = writeSkillMd("""
            # Code Review
            You are a code reviewer.
            Check for bugs.
            """);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill).isNotNull();
        assertThat(skill.meta().name()).isEqualTo("my-skill");
        assertThat(skill.meta().description()).isEqualTo("You are a code reviewer.");
        assertThat(skill.meta().mode()).isEqualTo("inline");
        assertThat(skill.promptBody()).contains("You are a code reviewer.");
    }

    @Test
    void descriptionFallbackToFirstNonHeadingLine() throws IOException {
        Path dir = writeSkillMd("""
            # Title Heading
            This is the description line.
            More content.
            """);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill.meta().description()).isEqualTo("This is the description line.");
    }

    @Test
    void frontmatterYamlErrorDegradesGracefully() throws IOException {
        Path dir = writeSkillMd("""
            ---
            name: [invalid yaml
            description: test
            ---
            Body text.
            """);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill).isNotNull();
        assertThat(skill.meta().name()).isEqualTo("my-skill");
        assertThat(skill.promptBody()).contains("Body text.");
    }

    @Test
    void contextForkBackwardCompatible() throws IOException {
        Path dir = writeSkillMd("""
            ---
            name: fork-skill
            context: fork
            ---
            Body.
            """);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill.meta().mode()).isEqualTo("fork");
    }

    @Test
    void parseReturnsNullWhenNoSkillMd() throws IOException {
        Path dir = tempDir.resolve("empty");
        Files.createDirectories(dir);
        var skill = SkillCatalog.parseSkillMD(dir);
        assertThat(skill).isNull();
    }
}
