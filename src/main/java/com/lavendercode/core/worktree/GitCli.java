package com.lavendercode.core.worktree;

import java.nio.file.Path;
import java.util.List;

public interface GitCli {
    String run(Path cwd, List<String> args) throws GitCliException;

    final class GitCliException extends RuntimeException {
        public GitCliException(String msg) {
            super(msg);
        }

        public GitCliException(String msg, Throwable c) {
            super(msg, c);
        }
    }
}
