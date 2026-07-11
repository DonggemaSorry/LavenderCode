package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class BlacklistLayerTest {
    private final BlacklistLayer layer = new BlacklistLayer();

    @TempDir Path projectRoot;

    private ToolCallContext bash(String cmd) {
        return ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", cmd)), projectRoot);
    }

    @Test
    void blocksRmRfRoot() {
        var d = layer.evaluate(bash("rm -rf /")).orElseThrow();
        assertThat(d).isInstanceOf(PermissionDecision.Deny.class);
        assertThat(((PermissionDecision.Deny) d).source()).isEqualTo("BLACKLIST");
    }

    @Test
    void blocksRmRfHome() {
        var d = layer.evaluate(bash("rm -rf ~")).orElseThrow();
        assertThat(d).isInstanceOf(PermissionDecision.Deny.class);
        assertThat(((PermissionDecision.Deny) d).source()).isEqualTo("BLACKLIST");
    }

    @Test
    void blocksMkfs() {
        var d = layer.evaluate(bash("mkfs.ext4 /dev/sda1")).orElseThrow();
        assertThat(d).isInstanceOf(PermissionDecision.Deny.class);
    }

    @Test
    void blocksForkBomb() {
        var d = layer.evaluate(bash(":(){ :|:& };:")).orElseThrow();
        assertThat(d).isInstanceOf(PermissionDecision.Deny.class);
    }

    @Test
    void blocksDevSdaRedirect() {
        var d = layer.evaluate(bash("echo x > /dev/sda")).orElseThrow();
        assertThat(d).isInstanceOf(PermissionDecision.Deny.class);
    }

    @Test
    void allowsGitStatus() {
        assertThat(layer.evaluate(bash("git status"))).isEmpty();
    }

    @Test
    void skipsNonCommandTools() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "read_file", Map.of("path", "a.txt")), projectRoot);
        assertThat(layer.evaluate(ctx)).isEmpty();
    }
}
