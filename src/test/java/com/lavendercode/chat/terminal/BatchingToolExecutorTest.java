package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class BatchingToolExecutorTest {
    @TempDir
    Path root;

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void shouldRunReadOnlyToolsConcurrently() throws Exception {
        ToolRegistry.register(slowTool("read_file", 200, true));
        ToolRegistry.register(slowTool("search_file", 200, true));
        var executor = PermissionTestSupport.bypassExecutor(30, 120, root);
        var calls = List.of(
            new ToolCall("c1", "read_file", Map.of("path", "a")),
            new ToolCall("c2", "search_file", Map.of("pattern", "x")));
        long start = System.currentTimeMillis();
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        long elapsed = System.currentTimeMillis() - start;
        assertThat(results).hasSize(2);
        assertThat(elapsed).isLessThan(400);
    }

    @Test
    void shouldRunSideEffectToolsSerially() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        ToolRegistry.register(orderTrackTool("write_file", order, 100));
        ToolRegistry.register(orderTrackTool("edit_file", order, 100));
        var executor = PermissionTestSupport.bypassExecutor(30, 120, root);
        var calls = List.of(
            new ToolCall("c1", "write_file", Map.of("path", "a", "content", "x")),
            new ToolCall("c2", "edit_file", Map.of("path", "a", "old", "x", "new", "y")));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(2);
        assertThat(order).containsExactly("write_file-start", "write_file-end", "edit_file-start", "edit_file-end");
    }

    @Test
    void shouldPreserveOrderWithMixedBatch() {
        ToolRegistry.register(slowTool("read_file", 50, true));
        ToolRegistry.register(orderTrackTool("write_file", Collections.synchronizedList(new ArrayList<>()), 50));
        ToolRegistry.register(slowTool("search_file", 50, true));
        var executor = PermissionTestSupport.bypassExecutor(30, 120, root);
        var calls = List.of(
            new ToolCall("c1", "read_file", Map.of("path", "a")),
            new ToolCall("c2", "write_file", Map.of("path", "b", "content", "x")),
            new ToolCall("c3", "search_file", Map.of("pattern", "x")));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).success()).isTrue();
        assertThat(results.get(2).success()).isTrue();
    }

    @Test
    void shouldReturnCancelledForUnexecutedOnCancel() {
        ToolRegistry.register(slowTool("read_file", 5000, true));
        var executor = PermissionTestSupport.bypassExecutor(30, 120, root);
        var cancelFlag = new AtomicBoolean(true);
        var calls = List.of(new ToolCall("c1", "read_file", Map.of("path", "a")));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, cancelFlag);
        assertThat(results.get(0).errorCategory()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        var executor = PermissionTestSupport.bypassExecutor(30, 120, root);
        var calls = List.of(new ToolCall("c1", "fake_tool", Map.of()));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        assertThat(results.get(0).errorCategory()).isEqualTo("TOOL_NOT_FOUND");
    }

    private Tool slowTool(String name, long delayMs, boolean readOnly) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of()); }
            @Override public ToolResult execute(Map<String, Object> p) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ToolResult.success(name + "-ok", ""); }
            @Override public boolean isReadOnly() { return readOnly; }
        };
    }

    private Tool orderTrackTool(String name, List<String> order, long delayMs) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of()); }
            @Override public ToolResult execute(Map<String, Object> p) {
                order.add(name + "-start");
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                order.add(name + "-end");
                return ToolResult.success(name + "-ok", ""); }
        };
    }
}
