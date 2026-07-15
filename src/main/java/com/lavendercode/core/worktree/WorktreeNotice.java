package com.lavendercode.core.worktree;

import java.nio.file.Path;

public final class WorktreeNotice {
    private WorktreeNotice() {}

    public static String build(Path parentCwd, Path wtPath) {
        return """
            <worktree-context>
            你当前在一个独立的 Git Worktree 副本中工作，与父 Agent 隔离。
            - 父目录: %s
            - 你的工作目录: %s
            - 父 Agent 提到的绝对路径基于父目录，你需要翻译成本地路径（替换前缀）再读写
            - 编辑文件前，必须先在本地 Worktree 重新 `read_file` 一次，避免使用过时内容
            </worktree-context>
            """.formatted(parentCwd, wtPath);
    }
}
