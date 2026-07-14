package com.lavendercode.core.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {
    @AfterEach
    void cleanup() {
        ToolRegistry.clearFilter();
        ToolRegistry.clear();
    }

    @Test
    void registerAndGet() {
        ToolRegistry.register(dummy("t"));
        assertThat(ToolRegistry.get("t")).isNotNull();
        assertThat(ToolRegistry.has("t")).isTrue();
    }

    @Test
    void nullForUnknown() {
        assertThat(ToolRegistry.get("x")).isNull();
    }

    @Test
    void overwriteOnDuplicate() {
        ToolRegistry.register(dummy("d"));
        Tool t2 = dummy("d");
        ToolRegistry.register(t2);
        assertThat(ToolRegistry.get("d")).isSameAs(t2);
        assertThat(ToolRegistry.size()).isEqualTo(1);
    }

    @Test
    void unregister() {
        ToolRegistry.register(dummy("u"));
        ToolRegistry.unregister("u");
        assertThat(ToolRegistry.has("u")).isFalse();
    }

    @Test
    void exportDefs() {
        ToolRegistry.register(dummy("a"));
        ToolRegistry.register(dummy("b"));
        assertThat(ToolRegistry.export()).hasSize(2)
            .extracting(ToolDefinition::name).contains("a", "b");
    }

    @Test
    void clear() {
        ToolRegistry.register(dummy("x"));
        ToolRegistry.clear();
        assertThat(ToolRegistry.size()).isZero();
    }

    private Tool dummy(String name) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return "d"; }
            @Override public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of("path",
                    new ToolParameterSchema.PropertyDef("string", "路径", null, null)),
                    List.of("path"));
            }
            @Override public ToolResult execute(Map<String, Object> p) {
                return ToolResult.success("ok", "");
            }
        };
    }
}
