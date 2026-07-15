package com.lavendercode.core.worktree;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;

public record WorktreeSession(
    @JsonProperty("original_cwd") Path originalCwd,
    @JsonProperty("worktree_path") Path worktreePath,
    @JsonProperty("worktree_name") String worktreeName,
    @JsonProperty("original_branch") String originalBranch,
    @JsonProperty("original_head_commit") String originalHeadCommit,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("hook_based") boolean hookBased
) {}
