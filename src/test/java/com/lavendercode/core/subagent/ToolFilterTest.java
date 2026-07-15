package com.lavendercode.core.subagent;

import com.lavendercode.core.tool.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ToolFilterTest {

    @BeforeEach
    void setup() {
        ToolRegistry.clear();
        ToolRegistry.register(stub("Agent"));
        ToolRegistry.register(stub("read_file"));
        ToolRegistry.register(stub("write_file"));
        ToolRegistry.register(stub("execute_command"));
        ToolRegistry.register(stub("install_skill"));
        ToolRegistry.register(stub("mcp_server_tool"));
    }

    @AfterEach
    void cleanup() {
        ToolRegistry.clear();
    }

    @Test
    void definedAgentRemovesAgentTool() {
        var def = new AgentDefinition("explore", "d", List.of(), List.of("write_file"),
            "inherit", 25, null, false, "body", AgentCatalog.Source.BUILTIN);
        Set<String> allowed = ToolFilter.filter(def, false, false);
        assertThat(allowed).contains("read_file").doesNotContain("Agent", "write_file");
    }

    @Test
    void forkBackgroundAddsAgentBackForIntercept() {
        var def = AgentDefinition.forkBase("boiler");
        Set<String> allowed = ToolFilter.filter(def, true, true);
        assertThat(allowed).contains("Agent", "read_file", "write_file");
    }

    static Tool stub(String name) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return "d"; }
            public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }
            public ToolResult execute(Map<String, Object> p) {
                return ToolResult.success("ok", "");
            }
        };
    }
}
