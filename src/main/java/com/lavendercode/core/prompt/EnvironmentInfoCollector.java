package com.lavendercode.core.prompt;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class EnvironmentInfoCollector {
    public static String collect(String modelName, String appVersion) {
        var lines = new ArrayList<String>();
        lines.add("## Environment");
        lines.add("- Working directory: " + escape(System.getProperty("user.dir")));
        lines.add("- Platform: " + System.getProperty("os.name"));
        lines.add("- Date: " + LocalDate.now());
        String git = collectGitStatus();
        if (git != null) lines.add("- Git status: " + git);
        lines.add("- Application version: " + appVersion);
        lines.add("- Current model: " + modelName);
        return String.join("\n", lines);
    }

    private static String collectGitStatus() {
        try {
            var pb = new ProcessBuilder("git", "status", "--short", "--branch")
                .directory(new File(System.getProperty("user.dir")))
                .redirectErrorStream(true);
            var p = pb.start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return out.isEmpty() ? "clean" : out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String path) {
        if (path == null) return "unknown";
        return path.replace("\\", "/");
    }
}
