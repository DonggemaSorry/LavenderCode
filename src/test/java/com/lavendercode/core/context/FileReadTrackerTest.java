package com.lavendercode.core.context;

import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class FileReadTrackerTest {
    @Test
    void keepsLatestFiveByRecency() {
        FileReadTracker tracker = new FileReadTracker();
        for (int i = 0; i < 7; i++) {
            tracker.record(
                List.of(new ToolCall("c" + i, ContextConstants.READ_FILE_TOOL_NAME,
                    Map.of("path", "f" + i + ".txt"))),
                List.of(ToolResult.success("ok", "body" + i)));
        }
        List<FileSnapshot> latest = tracker.latest(ContextConstants.MAX_FILE_SNAPSHOTS);
        assertThat(latest).hasSize(5);
        assertThat(latest.get(0).path()).isEqualTo("f6.txt");
    }

    @Test
    void stripsLineNumberPrefixes() {
        FileReadTracker tracker = new FileReadTracker();
        tracker.record(
            List.of(new ToolCall("c1", ContextConstants.READ_FILE_TOOL_NAME,
                Map.of("path", "a.txt"))),
            List.of(ToolResult.success("ok", "     1|hello\n     2|world")));
        assertThat(tracker.latest(1).get(0).content()).isEqualTo("hello\nworld");
    }

    @Test
    void ignoresNonReadFileTools() {
        FileReadTracker tracker = new FileReadTracker();
        tracker.record(
            List.of(new ToolCall("c1", "grep", Map.of("pattern", "x"))),
            List.of(ToolResult.success("ok", "output")));
        assertThat(tracker.latest(5)).isEmpty();
    }

    @Test
    void toleratesMismatchedCallAndResultCounts() {
        FileReadTracker tracker = new FileReadTracker();
        tracker.record(
            List.of(new ToolCall("c1", ContextConstants.READ_FILE_TOOL_NAME, Map.of("path", "a.txt"))),
            List.of());
        assertThat(tracker.latest(5)).isEmpty();
    }
}
