package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.PermissionMode;
import java.util.List;

public record AgentDefinition(
    String name,
    String description,
    List<String> tools,
    List<String> disallowedTools,
    String model,
    int maxTurns,
    PermissionMode permissionMode,
    boolean forceBackground,
    String systemPrompt,
    AgentCatalog.Source source
) {
    public static final int DEFAULT_MAX_TURNS = 25;

    public static AgentDefinition forkBase(String boilerplateBody) {
        return new AgentDefinition(
            "__fork__",
            "Fork sub-agent",
            List.of(),
            List.of(),
            "inherit",
            DEFAULT_MAX_TURNS,
            PermissionMode.DEFAULT,
            true,
            boilerplateBody,
            AgentCatalog.Source.BUILTIN);
    }
}
