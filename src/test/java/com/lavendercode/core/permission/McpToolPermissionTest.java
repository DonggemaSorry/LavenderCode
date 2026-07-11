package com.lavendercode.core.permission;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolParameterSchema;
import com.lavendercode.core.tool.ToolRegistry;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class McpToolPermissionTest {
    @TempDir
    Path projectRoot;

    @BeforeEach
    void setUp() {
        registerMcpTool("mcp__github__search", true);
        registerMcpTool("mcp__github__create_issue", false);
    }

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void allowRuleMatchesMcpToolGlob() {
        var layer = RuleEngineLayer.ofRules(
            List.of(PermissionRule.parse("mcp__github__*(*)", PermissionRule.Effect.ALLOW)));
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__github__search", Map.of()), projectRoot);
        assertThat(layer.evaluate(ctx)).isPresent().get().isInstanceOf(PermissionDecision.Allow.class);
    }

    @Test
    void readOnlyMcpToolAllowedInDefaultMode() {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.DENY, projectRoot, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__github__search", Map.of()), projectRoot);
        assertThat(pipeline.evaluate(ctx, new AtomicBoolean(false)).allowed()).isTrue();
    }

    @Test
    void nonReadOnlyMcpToolAsksInDefaultMode() {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.ALLOW_ONCE, projectRoot, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__github__create_issue", Map.of()), projectRoot);
        assertThat(pipeline.evaluate(ctx, new AtomicBoolean(false)).allowed()).isTrue();
    }

    @Test
    void nonReadOnlyMcpToolDeniedWhenUserDenies() {
        var pipeline = PermissionPipeline.create(
            PermissionConfig.empty(), () -> PermissionMode.DEFAULT,
            (req, f) -> HitlChoice.DENY, projectRoot, r -> {});
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__github__create_issue", Map.of()), projectRoot);
        var outcome = pipeline.evaluate(ctx, new AtomicBoolean(false));
        assertThat(outcome.denied()).isTrue();
        assertThat(outcome.deny().source()).isEqualTo("USER");
    }

    private static void registerMcpTool(String name, boolean readOnly) {
        ToolRegistry.register(new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "mock mcp";
            }

            @Override
            public ToolParameterSchema parameters() {
                return new ToolParameterSchema("object", Map.of(), List.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> params) {
                return ToolResult.success("ok", "");
            }

            @Override
            public boolean isReadOnly() {
                return readOnly;
            }
        });
    }
}
