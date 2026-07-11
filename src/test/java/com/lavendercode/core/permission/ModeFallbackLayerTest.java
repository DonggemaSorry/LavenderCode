package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class ModeFallbackLayerTest {
    @TempDir Path root;
    private final AtomicReference<PermissionMode> mode = new AtomicReference<>(PermissionMode.DEFAULT);

    @Test
    void defaultModeReadAllowWriteAsk() {
        var layer = new ModeFallbackLayer(mode::get);
        var read = ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "a")), root);
        var write = ToolMetadata.from(new ToolCall("2", "write_file", Map.of("path", "a", "content", "x")), root);
        assertThat(layer.evaluate(read).orElseThrow()).isInstanceOf(PermissionDecision.Allow.class);
        assertThat(layer.evaluate(write).orElseThrow()).isInstanceOf(PermissionDecision.Ask.class);
    }

    @Test
    void acceptEditsAllowsFileWrite() {
        mode.set(PermissionMode.ACCEPT_EDITS);
        var layer = new ModeFallbackLayer(mode::get);
        var write = ToolMetadata.from(new ToolCall("1", "write_file", Map.of("path", "a", "content", "x")), root);
        assertThat(layer.evaluate(write).orElseThrow()).isInstanceOf(PermissionDecision.Allow.class);
    }

    @Test
    void bypassAllowsCommand() {
        mode.set(PermissionMode.BYPASS_PERMISSIONS);
        var layer = new ModeFallbackLayer(mode::get);
        var bash = ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "echo hi")), root);
        assertThat(layer.evaluate(bash).orElseThrow()).isInstanceOf(PermissionDecision.Allow.class);
    }

    @Test
    void neverProducesDeny() {
        var layer = new ModeFallbackLayer(mode::get);
        for (PermissionMode m : PermissionMode.values()) {
            mode.set(m);
            for (ToolCategory c : ToolCategory.values()) {
                var decision = layer.evaluate(fakeCtx(c)).orElseThrow();
                assertThat(decision).isNotInstanceOf(PermissionDecision.Deny.class);
            }
        }
    }

    private ToolCallContext fakeCtx(ToolCategory cat) {
        return switch (cat) {
            case READ_ONLY -> ToolMetadata.from(new ToolCall("1", "read_file", Map.of("path", "a")), root);
            case FILE_WRITE -> ToolMetadata.from(new ToolCall("1", "write_file", Map.of("path", "a", "content", "x")), root);
            case COMMAND -> ToolMetadata.from(new ToolCall("1", "execute_command", Map.of("command", "echo")), root);
        };
    }
}
