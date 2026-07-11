package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class BatchingToolExecutorPermissionTest {
    @TempDir
    Path root;

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void denyReturnsPermissionErrorWithoutExecuting() {
        ToolRegistry.register(new SpyTool("write_file"));
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (r, f) -> HitlChoice.DENY, root, ru -> {});
        var exec = new BatchingToolExecutor(5, 5, pipeline, root);
        var calls = List.of(new ToolCall("1", "write_file", Map.of("path", "a", "content", "x")));
        var results = exec.execute(calls, e -> {}, new AtomicBoolean(false));
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).errorCategory()).isEqualTo("PERMISSION_DENIED");
        assertThat(SpyTool.executed).isFalse();
    }

    static class SpyTool implements Tool {
        static boolean executed;
        private final String n;

        SpyTool(String n) {
            this.n = n;
            executed = false;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public String description() {
            return "d";
        }

        @Override
        public ToolParameterSchema parameters() {
            return new ToolParameterSchema("object", Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> p) {
            executed = true;
            return ToolResult.success("ok", "");
        }
    }
}
