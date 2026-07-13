package com.lavendercode.core.context;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHandleTest {
    @Test
    void rebindUpdatesPaths(@TempDir Path root) throws Exception {
        SessionPaths p1 = new SessionPaths(root, "20260101-120000-aaaa");
        p1.ensureDirectories();
        SessionHandle h = new SessionHandle(
            root, "20260101-120000-aaaa", p1, NoOpContextManager.INSTANCE);

        h.rebind("20260102-130000-bbbb");

        assertThat(h.sessionId()).isEqualTo("20260102-130000-bbbb");
        assertThat(h.paths().sessionRoot())
            .isEqualTo(root.resolve(".lavendercode/sessions/20260102-130000-bbbb"));
        assertThat(Files.isDirectory(h.paths().toolResultsDir())).isTrue();
        assertThat(h.contextManager()).isSameAs(NoOpContextManager.INSTANCE);
        assertThat(h.projectRoot()).isEqualTo(root);
    }

    @Test
    void layer1FollowsRebind(@TempDir Path root) throws Exception {
        SessionPaths p1 = new SessionPaths(root, "20260101-120000-aaaa");
        p1.ensureDirectories();
        SessionHandle h = new SessionHandle(
            root, "20260101-120000-aaaa", p1, NoOpContextManager.INSTANCE);
        SessionManager sm = new InMemorySessionManager();
        String big = "X".repeat(60_000);
        sm.addToolMessages(
            List.of(new ToolCall("toolu_rebind", "grep", Map.of())),
            List.of(ToolResult.success("ok", big)));
        Layer1Offloader offloader = new Layer1Offloader(sm, h::paths, new ReplacementLedger());

        h.rebind("20260102-130000-bbbb");
        int replaced = offloader.offloadAndSnip();

        assertThat(replaced).isEqualTo(1);
        assertThat(p1.fileExists("toolu_rebind")).isFalse();
        assertThat(h.paths().fileExists("toolu_rebind")).isTrue();
        assertThat(h.paths().toolResultPath("toolu_rebind"))
            .startsWith(root.resolve(".lavendercode/sessions/20260102-130000-bbbb"));
    }
}
