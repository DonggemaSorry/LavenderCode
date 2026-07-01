package com.lavendercode.chat.terminal;

import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class BatchingToolExecutorTest {
    @AfterEach
    void cleanup() { ToolRegistry.clear(); }

    @Test
    void shouldRunReadOnlyToolsConcurrently() throws Exception {
        ToolRegistry.register(slowTool("ro1", 200, true));
        ToolRegistry.register(slowTool("ro2", 200, true));
        var executor = new BatchingToolExecutor(30, 120);
        var calls = List.of(
            new ToolCall("c1", "ro1", Map.of()),
            new ToolCall("c2", "ro2", Map.of()));
        long start = System.currentTimeMillis();
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        long elapsed = System.currentTimeMillis() - start;
        assertThat(results).hasSize(2);
        // 并发: 总耗时≈最慢者(200ms)，不是串行(400ms)
        assertThat(elapsed).isLessThan(400);
    }

    @Test
    void shouldRunSideEffectToolsSerially() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        ToolRegistry.register(orderTrackTool("write1", order, 100));
        ToolRegistry.register(orderTrackTool("write2", order, 100));
        var executor = new BatchingToolExecutor(30, 120);
        var calls = List.of(
            new ToolCall("c1", "write1", Map.of()),
            new ToolCall("c2", "write2", Map.of()));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(2);
        // 串行: write1 完成后才开始 write2
        assertThat(order).containsExactly("write1-start", "write1-end", "write2-start", "write2-end");
    }

    @Test
    void shouldPreserveOrderWithMixedBatch() {
        ToolRegistry.register(slowTool("ro", 50, true));
        ToolRegistry.register(orderTrackTool("write", Collections.synchronizedList(new ArrayList<>()), 50));
        var executor = new BatchingToolExecutor(30, 120);
        var calls = List.of(
            new ToolCall("c1", "ro", Map.of()),
            new ToolCall("c2", "write", Map.of()),
            new ToolCall("c3", "ro", Map.of()));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, new AtomicBoolean(false));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(1).success()).isTrue();
        assertThat(results.get(2).success()).isTrue();
    }

    @Test
    void shouldReturnCancelledForUnexecutedOnCancel() {
        ToolRegistry.register(slowTool("slow", 5000, true));
        var executor = new BatchingToolExecutor(30, 120);
        var cancelFlag = new AtomicBoolean(true); // pre-cancelled
        var calls = List.of(new ToolCall("c1", "slow", Map.of()));
        List<ToolResult> results = executor.execute(calls, (ev) -> {}, cancelFlag);
        assertThat(results.get(0).errorCategory()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        var executor = new BatchingToolExecutor(30, 120);
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
