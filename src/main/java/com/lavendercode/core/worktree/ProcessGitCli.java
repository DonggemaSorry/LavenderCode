package com.lavendercode.core.worktree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProcessGitCli implements GitCli {
    @Override
    public String run(Path cwd, List<String> args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(cwd.toFile());
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().put("GIT_ASKPASS", "");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            if (code != 0) {
                throw new GitCliException("git " + args + " failed: " + out.strip());
            }
            return out.strip();
        } catch (GitCliException e) {
            throw e;
        } catch (Exception e) {
            throw new GitCliException("git 执行失败: " + e.getMessage(), e);
        }
    }
}
