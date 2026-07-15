package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeammateInfo(
    String name,
    String agentId,
    String agentType,
    String model,
    Path worktreePath,
    String branch,
    BackendType backendType,
    String paneId,
    Boolean isActive,
    boolean planModeRequired,
    Path sessionDir
) {
    public static TeammateInfo leadPlaceholder() {
        return new TeammateInfo(
            "lead", "lead", "", "", null, "",
            BackendType.IN_PROCESS, "", null, false, null);
    }

    public boolean isEffectivelyActive() {
        return isActive == null || Boolean.TRUE.equals(isActive);
    }
}
