package com.lavendercode.core.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LocalPermissionWriter {

    private LocalPermissionWriter() {}

    public static void appendRule(Path localConfigPath, String ruleText, PermissionRule.Effect effect) throws IOException {
        Files.createDirectories(localConfigPath.getParent());
        List<String> lines = new ArrayList<>();
        if (Files.exists(localConfigPath)) {
            lines.addAll(Files.readAllLines(localConfigPath));
        } else {
            lines.add("rules:");
        }
        if (!lines.stream().anyMatch(line -> line.trim().startsWith("rules:"))) {
            lines.add("rules:");
        }
        String effectText = effect == PermissionRule.Effect.DENY ? "deny" : "allow";
        lines.add("  - \"" + ruleText + "\": " + effectText);
        Files.writeString(localConfigPath, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }
}
