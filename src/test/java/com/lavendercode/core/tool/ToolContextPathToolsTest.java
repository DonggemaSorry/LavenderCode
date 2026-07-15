package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ToolContextPathToolsTest {

    @TempDir Path temp;

    @Test
    void readFileRespectsCtxCwd() throws Exception {
        Path wt = temp.resolve("wt");
        Files.createDirectories(wt);
        Files.writeString(wt.resolve("f.txt"), "hello\n");
        ToolContext ctx = ToolContext.empty().withCwd(wt);
        ToolResult r = new ReadFileTool().execute(ctx, Map.of("path", "f.txt"));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("hello");
    }

    @Test
    void executeCommandUsesCtxCwd() throws Exception {
        Path wt = temp.resolve("wt2");
        Files.createDirectories(wt);
        Files.writeString(wt.resolve("marker.txt"), "m");
        ToolContext ctx = ToolContext.empty().withCwd(wt);
        String cmd = System.getProperty("os.name").toLowerCase().contains("win") ? "dir" : "ls";
        ToolResult r = new ExecuteCommandTool(true, 10, 10000)
            .execute(ctx, Map.of("command", cmd));
        assertThat(r.success()).isTrue();
        assertThat(r.content()).contains("marker.txt");
    }
}
