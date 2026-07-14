package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class LoadFromYamlAndPromptTest {

    @TempDir
    Path tempDir;

    private Path createSkillDir(String yamlContent, String promptContent) throws IOException {
        Path dir = tempDir.resolve("yaml-skill");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), yamlContent);
        Files.writeString(dir.resolve("prompt.md"), promptContent);
        return dir;
    }

    @Test
    void parseYamlAndPrompt() throws IOException {
        Path dir = createSkillDir("""
            name: commit
            description: 提交代码变更
            whenToUse: 当用户需要提交时
            tags:
              - git
            allowedTools:
              - execute_command
              - write_file
            mode: inline
            """,
            "You are a commit assistant.\nFollow the SOP.");
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill).isNotNull();
        assertThat(skill.meta().name()).isEqualTo("commit");
        assertThat(skill.meta().description()).isEqualTo("提交代码变更");
        assertThat(skill.meta().whenToUse()).isEqualTo("当用户需要提交时");
        assertThat(skill.meta().tags()).containsExactly("git");
        assertThat(skill.meta().allowedTools()).containsExactly("execute_command", "write_file");
        assertThat(skill.meta().mode()).isEqualTo("inline");
        assertThat(skill.meta().model()).isNull();
        assertThat(skill.meta().forkContext()).isEqualTo("none");
    }

    @Test
    void phase1DoesNotLoadBody() throws IOException {
        Path dir = createSkillDir("""
            name: test
            description: test skill
            """,
            "Prompt body content.");
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill).isNotNull();
        assertThat(skill.promptBody()).isNull();
        assertThat(skill.bodyLoaded()).isFalse();
    }

    @Test
    void forkModeParsed() throws IOException {
        Path dir = createSkillDir("""
            name: fork-skill
            mode: fork
            forkContext: full
            """,
            "Fork body.");
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill.meta().mode()).isEqualTo("fork");
        assertThat(skill.meta().forkContext()).isEqualTo("full");
    }

    @Test
    void nameDefaultsToDirName() throws IOException {
        Path dir = tempDir.resolve("My Custom Skill");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), "description: test\n");
        Files.writeString(dir.resolve("prompt.md"), "body");
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill.meta().name()).isEqualTo("my-custom-skill");
    }

    @Test
    void modelFieldParsed() throws IOException {
        Path dir = createSkillDir("""
            name: test
            model: claude-3-opus
            """,
            "body");
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill.meta().model()).isEqualTo("claude-3-opus");
    }

    @Test
    void returnsNullWhenNoYamlOrMd() throws IOException {
        Path dir = tempDir.resolve("empty");
        Files.createDirectories(dir);
        var skill = SkillCatalog.loadFromYamlAndPrompt(dir);
        assertThat(skill).isNull();
    }
}
