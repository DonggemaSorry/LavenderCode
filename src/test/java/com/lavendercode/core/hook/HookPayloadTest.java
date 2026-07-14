package com.lavendercode.core.hook;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class HookPayloadTest {
    @Test
    void commonFields() {
        var p = HookPayload.builder(HookEvent.SessionStart)
            .sessionId("abc").cwd(Path.of("/tmp")).mode("default").build();
        assertThat(p.event()).isEqualTo(HookEvent.SessionStart);
        assertThat(p.fields().get("session_id")).isEqualTo("abc");
    }

    @Test
    void preToolUseFields() {
        var p = HookPayload.builder(HookEvent.PreToolUse)
            .sessionId("s").cwd(Path.of("/tmp")).mode("default")
            .put("tool_name", "write_file")
            .put("tool_input", Map.of("path", "Main.java"))
            .build();
        assertThat(p.fields().get("tool_name")).isEqualTo("write_file");
    }

    @Test
    void toMapReturnsAllFields() {
        var p = HookPayload.builder(HookEvent.Stop)
            .sessionId("s").cwd(Path.of("/tmp")).mode("default")
            .put("iter", 3).build();
        var m = p.toMap();
        assertThat(m).containsKey("event");
        assertThat(m).containsKey("iter");
        assertThat(m.get("event")).isEqualTo("Stop");
    }
}
