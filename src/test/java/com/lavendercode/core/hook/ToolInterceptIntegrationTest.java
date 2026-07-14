package com.lavendercode.core.hook;

import com.lavendercode.chat.terminal.BatchingToolExecutor;
import com.lavendercode.core.permission.*;
import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class ToolInterceptIntegrationTest {

    @TempDir
    Path root;

    private BatchingToolExecutor executorWithEngine(HookEngine engine) {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(),
            () -> PermissionMode.BYPASS_PERMISSIONS,
            (request, cancelFlag) -> HitlChoice.DENY,
            root,
            rules -> {});
        return new BatchingToolExecutor(30, 30, pipeline, root, engine);
    }

    @Test
    void preToolUseShellExit2BlocksTool() {
        var rule = new HookRule("block-wf", HookEvent.PreToolUse, null,
            new HookAction.Shell("echo blocked & exit 2"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var executor = executorWithEngine(engine);
        var calls = List.of(new ToolCall("c1", "read_file", Map.of("path", "test.java")));
        var results = executor.execute(calls, e -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).errorCategory()).isEqualTo("HOOK_BLOCKED");
        assertThat(results.get(0).summary()).contains("[hook block-wf]");
    }

    @Test
    void preToolUseShellExit0AllowsTool() {
        var rule = new HookRule("allow", HookEvent.PreToolUse, null,
            new HookAction.Shell("exit 0"), false, false, Duration.ofSeconds(30));
        var engine = new HookEngineImpl(new HookConfig(List.of(rule), List.of("test")));
        var executor = executorWithEngine(engine);
        // Note: read_file is not registered, so we get TOOL_NOT_FOUND, but NOT hook block
        var calls = List.of(new ToolCall("c1", "read_file", Map.of("path", "test.java")));
        var results = executor.execute(calls, e -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(1);
        // Either success or tool error (not found), but NOT hook block
        if (results.get(0).errorCategory() != null) {
            assertThat(results.get(0).errorCategory()).isNotEqualTo("HOOK_BLOCKED");
        }
    }
}
