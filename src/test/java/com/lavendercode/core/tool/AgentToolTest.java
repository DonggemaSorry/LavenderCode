package com.lavendercode.core.tool;

import com.lavendercode.core.subagent.*;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AgentToolTest {

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void unknownSubagentTypeReturnsError() {
        var catalog = new AgentCatalog();
        var services = new SubAgentServices(catalog, null, null, (c, f) -> null, null);
        var tool = new AgentTool(services);
        ToolResult r = tool.execute(Map.of(
            "prompt", "do x",
            "description", "test",
            "subagent_type", "nope"));
        assertThat(r.success()).isFalse();
        assertThat(r.summary()).contains("未知 subagent_type");
    }
}
