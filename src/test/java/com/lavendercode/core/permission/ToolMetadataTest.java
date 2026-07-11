package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolMetadataTest {
    @TempDir Path projectRoot;

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
}
