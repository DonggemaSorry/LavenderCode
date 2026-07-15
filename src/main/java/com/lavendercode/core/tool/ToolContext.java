package com.lavendercode.core.tool;

import java.nio.file.Path;
import java.util.Optional;

public final class ToolContext {
    private final Path cwd;

    private ToolContext(Path cwd) {
        this.cwd = cwd;
    }

    public static ToolContext empty() {
        return new ToolContext(null);
    }

    public ToolContext withCwd(Path dir) {
        return new ToolContext(dir == null ? null : dir.toAbsolutePath().normalize());
    }

    public Optional<Path> cwd() {
        return Optional.ofNullable(cwd);
    }

    public Path resolvePath(String p) {
        if (p == null || p.isBlank() || ".".equals(p)) {
            return cwd != null ? cwd : Path.of("").toAbsolutePath().normalize();
        }
        Path path = Path.of(p);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path base = cwd != null ? cwd : Path.of("").toAbsolutePath();
        return base.resolve(path).normalize();
    }
}
