package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SandboxLayerTest {
    private final SandboxLayer layer = new SandboxLayer();

    @Test
    void allowsPathInsideProject(@TempDir Path root) throws Exception {
        Path file = root.resolve("src/A.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
        var ctx = ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "src/A.java")), root);
        assertThat(layer.evaluate(ctx)).isEmpty();
    }

    @Test
    void deniesPathOutsideProject(@TempDir Path root) {
        var ctx = ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "/etc/passwd")), root);
        assertThat(layer.evaluate(ctx)).isPresent();
        assertThat(layer.evaluate(ctx).orElseThrow()).isInstanceOf(PermissionDecision.Deny.class);
        assertThat(((PermissionDecision.Deny) layer.evaluate(ctx).orElseThrow()).source()).isEqualTo("SANDBOX");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void deniesSymlinkEscape(@TempDir Path root) throws Exception {
        Path outside = root.getParent().resolve("outside-secret");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("leak.txt"), "secret");
        Path link = root.resolve("link");
        Files.createSymbolicLink(link, outside);
        var ctx = ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "link/leak.txt")), root);
        assertThat(layer.evaluate(ctx)).isPresent();
    }

    @Test
    void allowsNewFileWithMissingParents(@TempDir Path root) {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "write_file", Map.of("path", "new/nested/file.txt", "content", "x")), root);
        assertThat(layer.evaluate(ctx)).isEmpty();
    }
}
