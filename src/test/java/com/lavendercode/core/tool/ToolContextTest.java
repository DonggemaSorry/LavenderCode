package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class ToolContextTest {

    @Test
    void resolvePathUsesCwdForRelative() {
        Path cwd = Path.of(System.getProperty("java.io.tmpdir"), "wt-ctx-test").toAbsolutePath().normalize();
        ToolContext ctx = ToolContext.empty().withCwd(cwd);
        assertThat(ctx.resolvePath("a.txt")).isEqualTo(cwd.resolve("a.txt").normalize());
        assertThat(ctx.resolvePath(cwd.resolve("b.txt").toString()).isAbsolute()).isTrue();
    }
}
