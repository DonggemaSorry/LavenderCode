package com.lavendercode.core.tool;

import com.lavendercode.core.config.Options;
import com.lavendercode.core.subagent.*;
import com.lavendercode.core.task.TaskManager;
import org.junit.jupiter.api.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class AgentToolForkTest {

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void forkInForkContextReturnsNestedError() {
        var services = minimalServices();
        var tool = new AgentTool(services);
        ToolResult r = SubAgentCallContext.run(SubAgentCallContext.Kind.FORK, () ->
            tool.execute(Map.of("prompt", "task", "description", "d")));
        assertThat(r.success()).isFalse();
        assertThat(r.summary()).contains("不能再启动 Agent");
    }

    @Test
    void forkWithBoilerplateHistoryReturnsNestedError() {
        var services = new SubAgentServices(
            new AgentCatalog(), null, null, PathHolder.ROOT, new Options(),
            null, null, new TaskManager(), null, null,
            () -> java.util.List.of(new com.lavendercode.core.provider.Message(
                com.lavendercode.core.provider.Role.USER,
                ForkBoilerplate.format("prior"))));
        var tool = new AgentTool(services);
        ToolResult r = tool.execute(Map.of("prompt", "task", "description", "d"));
        assertThat(r.success()).isFalse();
        assertThat(r.summary()).contains("不能再启动 Agent");
    }

    @Test
    void explicitBackgroundReturnsTaskId() throws Exception {
        var catalog = new AgentCatalog();
        catalog.register(new AgentDefinition(
            "explore", "d", java.util.List.of(), java.util.List.of(),
            "inherit", 5, null, false, "", "body", AgentCatalog.Source.BUILTIN));
        TaskManager mgr = new TaskManager();
        var services = new SubAgentServices(
            catalog, finiteProvider(), null, PathHolder.ROOT, new Options(),
            null, (req, f) -> com.lavendercode.core.permission.HitlChoice.ALLOW_ONCE,
            mgr, null, null, () -> java.util.List.of());
        var tool = new AgentTool(services);
        ToolResult r = tool.execute(Map.of(
            "prompt", "slow",
            "description", "bg test",
            "subagent_type", "explore",
            "run_in_background", true));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("async_launched");
        assertThat(r.content()).contains("task_id");
        String taskId = r.content().replaceAll("(?s).*\"task_id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        mgr.stop(taskId);
        Thread.sleep(100);
    }

    private static SubAgentServices minimalServices() {
        return new SubAgentServices(
            new AgentCatalog(), null, null, PathHolder.ROOT, new Options(),
            null, null, new TaskManager(), null, null, () -> java.util.List.of());
    }

    private static com.lavendercode.core.provider.LlmProvider finiteProvider() {
        return new com.lavendercode.core.provider.LlmProvider() {
            @Override
            public String protocol() { return "test"; }

            @Override
            public com.lavendercode.core.provider.StreamEventIterator streamChat(
                    java.util.List<com.lavendercode.core.provider.Message> history,
                    com.lavendercode.core.config.LlmConfig config) {
                return new com.lavendercode.core.provider.StreamEventIterator() {
                    private int step;
                    public boolean hasNext() { return step < 2; }
                    public com.lavendercode.core.provider.StreamEvent next() {
                        if (step++ == 0) {
                            return new com.lavendercode.core.provider.StreamEvent.ContentDelta("done");
                        }
                        return new com.lavendercode.core.provider.StreamEvent.StreamComplete();
                    }
                    public void close() {}
                };
            }
        };
    }

    private static final class PathHolder {
        static final java.nio.file.Path ROOT = java.nio.file.Path.of(".");
    }
}
