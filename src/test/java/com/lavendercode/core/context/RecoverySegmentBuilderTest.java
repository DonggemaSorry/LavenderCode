package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolDefinition;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RecoverySegmentBuilderTest {
    @Test
    void includesToolNamesAndReadFileHint() {
        FileReadTracker tracker = new FileReadTracker();
        tracker.record(
            List.of(new ToolCall("c1", ContextConstants.READ_FILE_TOOL_NAME, Map.of("path", "src/A.java"))),
            List.of(ToolResult.success("ok", "class A {}")));

        List<ToolDefinition> tools = List.of(
            new ToolDefinition("read_file", "read", Map.of()),
            new ToolDefinition("grep", "search", Map.of()));

        RecoverySegmentBuilder builder = new RecoverySegmentBuilder(tracker);
        List<Message> segments = builder.build(tools);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).content()).contains("### File: src/A.java");
        assertThat(segments.get(1).content()).contains("read_file");
        assertThat(segments.get(1).content()).contains("grep");
        assertThat(segments.get(2).content()).contains("read_file");
        assertThat(segments.get(2).content()).isEqualTo(RecoverySegmentBuilder.BOUNDARY_TEXT);
    }

    @Test
    void truncatesVeryLongFileContent() {
        FileReadTracker tracker = new FileReadTracker();
        int maxChars = (int) (ContextConstants.FILE_SNAPSHOT_MAX_TOKENS * ContextConstants.ESTIMATE_CHARS_PER_TOKEN);
        String huge = "Z".repeat(maxChars + 500);
        tracker.record(
            List.of(new ToolCall("c1", ContextConstants.READ_FILE_TOOL_NAME, Map.of("path", "big.txt"))),
            List.of(ToolResult.success("ok", huge)));

        RecoverySegmentBuilder builder = new RecoverySegmentBuilder(tracker);
        String fileSegment = builder.build(List.of()).get(0).content();

        assertThat(fileSegment).contains("(content truncated)");
        assertThat(fileSegment.length()).isLessThan(huge.length());
    }
}
