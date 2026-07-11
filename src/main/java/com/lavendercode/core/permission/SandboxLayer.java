package com.lavendercode.core.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SandboxLayer implements PermissionLayer {

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        if (ctx.sandboxPaths().isEmpty()) {
            return Optional.empty();
        }
        Path root = ctx.projectRoot().toAbsolutePath().normalize();
        for (Path target : ctx.sandboxPaths()) {
            Path resolved = resolveForSandbox(target, root);
            if (!resolved.startsWith(root)) {
                return Optional.of(new PermissionDecision.Deny(
                    "SANDBOX",
                    "路径超出项目根目录: " + target,
                    "请使用项目内路径"));
            }
        }
        return Optional.empty();
    }

    Path resolveForSandbox(Path target, Path projectRoot) {
        Path absolute = target.isAbsolute() ? target : projectRoot.resolve(target);
        absolute = absolute.toAbsolutePath().normalize();

        Path candidate = absolute;
        while (true) {
            if (Files.exists(candidate)) {
                if (Files.isSymbolicLink(candidate)) {
                    try {
                        return candidate.toRealPath().normalize();
                    } catch (IOException e) {
                        return candidate.normalize();
                    }
                }
                return candidate.normalize();
            }
            Path parent = candidate.getParent();
            if (parent == null) {
                return absolute;
            }
            candidate = parent;
        }
    }
}
