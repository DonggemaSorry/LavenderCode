package com.lavendercode.core.team;

import java.nio.file.Path;
import java.util.concurrent.Callable;

public record SpawnRequest(
    String teamName,
    String memberName,
    String agentId,
    Path worktreePath,
    Path sessionDir,
    String agentType,
    String model,
    String initialPrompt,
    boolean planModeRequired,
    Callable<String> work
) {}
