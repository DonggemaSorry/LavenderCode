package com.lavendercode.core.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsThreeLayersProjectRootFirst() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(projectRoot.resolve("LAVENDERCODE.md"), "project root");
        write(projectRoot.resolve(".lavendercode/LAVENDERCODE.md"), "project local");
        write(userHome.resolve(".lavendercode/LAVENDERCODE.md"), "user home");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("project root\n\nproject local\n\nuser home");
    }

    @Test
    void missingFilesSkipped() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(userHome.resolve(".lavendercode/LAVENDERCODE.md"), "user home only");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("user home only");
    }

    @Test
    void emptyLayersSkipped() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(projectRoot.resolve("LAVENDERCODE.md"), "   \n\t");
        write(projectRoot.resolve(".lavendercode/LAVENDERCODE.md"), "");
        write(userHome.resolve(".lavendercode/LAVENDERCODE.md"), "user home");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("user home");
    }

    @Test
    void expandsIncludeOnOwnLine() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(projectRoot.resolve("LAVENDERCODE.md"), "before\n@include docs/shared.md\nafter");
        write(projectRoot.resolve("docs/shared.md"), "included");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("before\nincluded\nafter");
    }

    @Test
    void depthLimitEmitsWarning() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(projectRoot.resolve("LAVENDERCODE.md"), "@include level2.md");
        write(projectRoot.resolve("level2.md"), "@include level3.md");
        write(projectRoot.resolve("level3.md"), "@include level4.md");
        write(projectRoot.resolve("level4.md"), "@include level5.md");
        write(projectRoot.resolve("level5.md"), "@include level6.md");
        Path level6 = projectRoot.resolve("level6.md");
        write(level6, "too deep");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).contains("@include level6.md");
        assertThat(result).contains("<!-- @include 超过最大嵌套深度，已跳过: " + normalized(level6) + " -->");
        assertThat(result).doesNotContain("too deep");
    }

    @Test
    void cycleEmitsWarning() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        Path entry = projectRoot.resolve("LAVENDERCODE.md");
        Path nested = projectRoot.resolve("nested.md");
        write(entry, "@include nested.md");
        write(nested, "@include LAVENDERCODE.md");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("<!-- @include 检测到环路，已跳过: " + normalized(entry) + " -->");
    }

    @Test
    void pathEscapeEmitsWarning() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        Path outside = tempDir.resolve("outside.md");
        write(projectRoot.resolve("LAVENDERCODE.md"), "@include ../outside.md");
        write(outside, "outside");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("<!-- @include 路径超出允许范围，已跳过: " + normalized(outside) + " -->");
    }

    @Test
    void inlineIncludeNotExpanded() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        write(projectRoot.resolve("LAVENDERCODE.md"), "Keep @include docs/shared.md inline");
        write(projectRoot.resolve("docs/shared.md"), "included");

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("Keep @include docs/shared.md inline");
    }

    @Test
    void binaryIncludeSkippedWithWarning() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path userHome = tempDir.resolve("home");
        Path binary = projectRoot.resolve("binary.md");
        write(projectRoot.resolve("LAVENDERCODE.md"), "before\n@include binary.md\nafter");
        Files.createDirectories(binary.getParent());
        Files.write(binary, new byte[] {'o', 'k', 0, 'n', 'o'});

        String result = InstructionLoader.load(projectRoot, userHome);

        assertThat(result).isEqualTo("before\n<!-- @include 检测到二进制文件，已跳过: "
            + normalized(binary) + " -->\nafter");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String normalized(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
