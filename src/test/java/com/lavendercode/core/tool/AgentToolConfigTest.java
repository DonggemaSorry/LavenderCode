package com.lavendercode.core.tool;

import com.lavendercode.core.config.Options;
import com.lavendercode.core.subagent.*;
import com.lavendercode.core.task.TaskManager;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AgentToolConfigTest {

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void backgroundDisabledRejectsFork() {
        Options opts = new Options(4096, "", true, false, 120, 30, 2000, 30000, 200, false);
        var services = new SubAgentServices(
            new AgentCatalog(), null, null, java.nio.file.Path.of("."), opts,
            null, null, new TaskManager(), null, null, () -> java.util.List.of());
        var tool = new AgentTool(services);
        ToolResult r = tool.execute(Map.of("prompt", "task", "description", "d"));
        assertThat(r.success()).isFalse();
        assertThat(r.summary()).contains("enableSubAgentBackground");
    }

    @Test
    void backgroundDisabledIgnoresRunInBackground() {
        var catalog = new AgentCatalog();
        catalog.register(new AgentDefinition(
            "explore", "d", java.util.List.of(), java.util.List.of(),
            "inherit", 5, null, false, "body", AgentCatalog.Source.BUILTIN));
        Options opts = new Options(4096, "", true, false, 120, 30, 2000, 30000, 200, false);
        var services = new SubAgentServices(
            catalog, fastProvider(), null, java.nio.file.Path.of("."), opts,
            null, (req, f) -> com.lavendercode.core.permission.HitlChoice.ALLOW_ONCE,
            new TaskManager(), null, null, () -> java.util.List.of());
        var tool = new AgentTool(services);
        ToolResult r = tool.execute(Map.of(
            "prompt", "x", "description", "d",
            "subagent_type", "explore",
            "run_in_background", true));
        assertThat(r.success()).isFalse();
    }

    private static com.lavendercode.core.provider.LlmProvider fastProvider() {
        return new com.lavendercode.core.provider.LlmProvider() {
            @Override
            public String protocol() { return "test"; }

            @Override
            public com.lavendercode.core.provider.StreamEventIterator streamChat(
                    java.util.List<com.lavendercode.core.provider.Message> history,
                    com.lavendercode.core.config.LlmConfig config) {
                return new com.lavendercode.core.provider.StreamEventIterator() {
                    private boolean done;
                    public boolean hasNext() { return !done; }
                    public com.lavendercode.core.provider.StreamEvent next() {
                        done = true;
                        return new com.lavendercode.core.provider.StreamEvent.ContentDelta("ok");
                    }
                    public void close() {}
                };
            }
        };
    }
}
