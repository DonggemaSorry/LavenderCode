package com.lavendercode.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class InstallSkillToolTest {

    @TempDir
    Path tempDir;

    @Test
    void parseGithubTreeUrl() {
        var source = InstallSkillTool.parseUrl(
            "https://github.com/owner/repo/tree/main/skills/commit");
        assertThat(source).isNotNull();
        assertThat(source.type()).isEqualTo(InstallSkillTool.SourceType.GITHUB_TREE);
        assertThat(source.owner()).isEqualTo("owner");
        assertThat(source.repo()).isEqualTo("repo");
        assertThat(source.branch()).isEqualTo("main");
        assertThat(source.path()).isEqualTo("skills/commit");
    }

    @Test
    void parseRawGithubUrl() {
        var source = InstallSkillTool.parseUrl(
            "https://raw.githubusercontent.com/owner/repo/main/SKILL.md");
        assertThat(source).isNotNull();
        assertThat(source.type()).isEqualTo(InstallSkillTool.SourceType.RAW_FILE);
        assertThat(source.owner()).isEqualTo("owner");
        assertThat(source.repo()).isEqualTo("repo");
        assertThat(source.branch()).isEqualTo("main");
        assertThat(source.path()).isEqualTo("SKILL.md");
    }

    @Test
    void parseUnsupportedUrlReturnsNull() {
        assertThat(InstallSkillTool.parseUrl("https://example.com/something")).isNull();
        assertThat(InstallSkillTool.parseUrl("not a url")).isNull();
    }

    @Test
    void validateLimitsAcceptsValidFiles() {
        var files = java.util.List.of(
            new InstallSkillTool.RemoteFile("SKILL.md", new byte[1024], 0),
            new InstallSkillTool.RemoteFile("prompt.md", new byte[2048], 1));
        InstallSkillTool.validateLimits(files); // 不抛异常
    }

    @Test
    void validateLimitsRejectsTooManyFiles() {
        var files = new java.util.ArrayList<InstallSkillTool.RemoteFile>();
        for (int i = 0; i < 65; i++) {
            files.add(new InstallSkillTool.RemoteFile("f" + i, new byte[100], 0));
        }
        assertThatThrownBy(() -> InstallSkillTool.validateLimits(files))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("文件数");
    }

    @Test
    void validateLimitsRejectsFileTooLarge() {
        var files = java.util.List.of(
            new InstallSkillTool.RemoteFile("big.bin", new byte[1024 * 1024 + 1], 0));
        assertThatThrownBy(() -> InstallSkillTool.validateLimits(files))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("单文件");
    }

    @Test
    void validateLimitsRejectsTotalTooLarge() {
        var files = new java.util.ArrayList<InstallSkillTool.RemoteFile>();
        for (int i = 0; i < 9; i++) {
            files.add(new InstallSkillTool.RemoteFile("f" + i, new byte[1024 * 1024], 0));
        }
        assertThatThrownBy(() -> InstallSkillTool.validateLimits(files))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("总大小");
    }

    @Test
    void validateLimitsRejectsDepthTooDeep() {
        var files = java.util.List.of(
            new InstallSkillTool.RemoteFile("a/b/c/d/e/f.txt", new byte[10], 5));
        assertThatThrownBy(() -> InstallSkillTool.validateLimits(files))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("目录深度");
    }

    @Test
    void ensureSkillMdRejectsMissing() {
        assertThatThrownBy(() -> InstallSkillTool.ensureSkillMd(tempDir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SKILL.md");
    }

    @Test
    void ensureSkillMdAcceptsPresent() throws IOException {
        Files.writeString(tempDir.resolve("SKILL.md"), "# Skill");
        InstallSkillTool.ensureSkillMd(tempDir); // 不抛异常
    }
}
