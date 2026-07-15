package com.lavendercode.core.worktree;

import java.nio.file.Path;

public record CleanupReport(boolean kept, Path path, String branch) {}
