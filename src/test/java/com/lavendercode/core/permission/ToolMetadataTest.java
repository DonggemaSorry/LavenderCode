package com.lavendercode.core.permission;

import com.lavendercode.core.tool.Tool;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolParameterSchema;
import com.lavendercode.core.tool.ToolRegistry;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolMetadataTest {
    @TempDir Path projectRoot;

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void mapsReadFileToFriendlyRead() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "read_file", Map.of("path", "src/A.java")),
            projectRoot);
        assertThat(ctx.friendlyName()).isEqualTo("Read");
        assertThat(ctx.category()).isEqualTo(ToolCategory.READ_ONLY);
        assertThat(ctx.matchKey()).isEqualTo("src/A.java");
        assertThat(ctx.sandboxPaths()).hasSize(1);
    }

    @Test
    void mapsExecuteCommandToBash() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "execute_command", Map.of("command", "git status")),
            projectRoot);
        assertThat(ctx.friendlyName()).isEqualTo("Bash");
        assertThat(ctx.category()).isEqualTo(ToolCategory.COMMAND);
        assertThat(ctx.matchKey()).isEqualTo("git status");
        assertThat(ctx.sandboxPaths()).isEmpty();
    }

    @Test
    void globUsesDirectoryParam() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "search_file", Map.of("pattern", "**/*.java", "directory", "src")),
            projectRoot);
        assertThat(ctx.friendlyName()).isEqualTo("Glob");
        assertThat(ctx.category()).isEqualTo(ToolCategory.READ_ONLY);
        assertThat(ctx.matchKey()).isEqualTo("src");
        assertThat(ctx.sandboxPaths()).hasSize(1);
    }

    @Test
    void globDefaultsDirectoryToDot() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "search_file", Map.of("pattern", "*.java")),
            projectRoot);
        assertThat(ctx.matchKey()).isEqualTo(".");
        assertThat(ctx.sandboxPaths()).hasSize(1);
    }

    @Test
    void unknownToolTreatedAsCommandForSafety() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "unknown_tool", Map.of()),
            projectRoot);
        assertThat(ctx.category()).isEqualTo(ToolCategory.COMMAND);
        assertThat(ctx.parseFailed()).isTrue();
    }

    @Test
    void parseErrorOnToolCallSetsParseFailed() {
        var ctx = ToolMetadata.from(
            new ToolCall("1", "read_file", Map.of(), "bad json"),
            projectRoot);
        assertThat(ctx.parseFailed()).isTrue();
    }

    @Test
    void mcpReadOnlyToolUsesReadOnlyCategory() {
        ToolRegistry.register(new Tool() {
            @Override
            public String name() {
                return "mcp__mock__echo";
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
            public ToolResult execute(Map<String, Object> params) {
                return ToolResult.success("ok", "");
            }

            @Override
            public boolean isReadOnly() {
                return true;
            }
        });
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__mock__echo", Map.of()), projectRoot);
        assertThat(ctx.category()).isEqualTo(ToolCategory.READ_ONLY);
        assertThat(ctx.friendlyName()).isEqualTo("mcp__mock__echo");
        assertThat(ctx.parseFailed()).isFalse();
    }

    @Test
    void mcpNonReadOnlyToolUsesCommandCategory() {
        ToolRegistry.register(new Tool() {
            @Override
            public String name() {
                return "mcp__mock__write";
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
            public ToolResult execute(Map<String, Object> params) {
                return ToolResult.success("ok", "");
            }

            @Override
            public boolean isReadOnly() {
                return false;
            }
        });
        var ctx = ToolMetadata.from(new ToolCall("1", "mcp__mock__write", Map.of()), projectRoot);
        assertThat(ctx.category()).isEqualTo(ToolCategory.COMMAND);
        assertThat(ctx.parseFailed()).isFalse();
    }
}
