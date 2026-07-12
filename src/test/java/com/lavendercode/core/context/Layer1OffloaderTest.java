package com.lavendercode.core.context;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class Layer1OffloaderTest {
    @TempDir Path projectRoot;

    @Test
    void singleToolResultOver50kIsOffloaded() throws Exception {
        SessionManager sm = new InMemorySessionManager();
        String big = "X".repeat(60_000);
        var tc = new ToolCall("toolu_big", "grep", Map.of());
        sm.addToolMessages(List.of(tc), List.of(ToolResult.success("ok", big)));

        SessionPaths paths = new SessionPaths(projectRoot, "sess1");
        ReplacementLedger ledger = new ReplacementLedger();
        Layer1Offloader offloader = new Layer1Offloader(sm, paths, ledger);

        int replaced = offloader.offloadAndSnip();
        assertThat(replaced).isEqualTo(1);
        assertThat(paths.fileExists("toolu_big")).isTrue();
        assertThat(Files.readString(paths.toolResultPath("toolu_big"))).isEqualTo(big);

        String content = sm.getHistory().stream()
            .filter(m -> "toolu_big".equals(m.toolCallId()))
            .findFirst().orElseThrow()
            .toolResults().get(0).content();
        assertThat(content).contains("Original size:");
        assertThat(content).contains("read_file");
        assertThat(content.getBytes(StandardCharsets.UTF_8).length)
            .isLessThan(big.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void aggregateOver200kOffloadsLargestFirst() {
        SessionManager sm = new InMemorySessionManager();
        String a = "A".repeat(80_000);
        String b = "B".repeat(80_000);
        String c = "C".repeat(80_000);
        sm.addToolMessages(
            List.of(
                new ToolCall("t1", "grep", Map.of()),
                new ToolCall("t2", "grep", Map.of()),
                new ToolCall("t3", "grep", Map.of())),
            List.of(
                ToolResult.success("ok", a),
                ToolResult.success("ok", b),
                ToolResult.success("ok", c)));

        SessionPaths paths = new SessionPaths(projectRoot, "sess2");
        ReplacementLedger ledger = new ReplacementLedger();
        Layer1Offloader offloader = new Layer1Offloader(sm, paths, ledger);
        offloader.offloadAndSnip();

        long offloaded = List.of("t1", "t2", "t3").stream().filter(paths::fileExists).count();
        assertThat(offloaded).isGreaterThanOrEqualTo(1);
    }

    @Test
    void secondRunDoesNotRewriteExistingFile() throws Exception {
        SessionManager sm = new InMemorySessionManager();
        String big = "Y".repeat(60_000);
        sm.addToolMessages(List.of(new ToolCall("id1", "grep", Map.of())),
            List.of(ToolResult.success("ok", big)));
        SessionPaths paths = new SessionPaths(projectRoot, "sess3");
        ReplacementLedger ledger = new ReplacementLedger();
        Layer1Offloader offloader = new Layer1Offloader(sm, paths, ledger);
        offloader.offloadAndSnip();
        var mtime1 = Files.getLastModifiedTime(paths.toolResultPath("id1"));
        Thread.sleep(50);
        offloader.offloadAndSnip();
        var mtime2 = Files.getLastModifiedTime(paths.toolResultPath("id1"));
        assertThat(mtime2).isEqualTo(mtime1);
    }
}
