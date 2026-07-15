package com.lavendercode.core.subagent;

import java.util.List;

public final class SubAgentConstants {

    public static final List<String> ALL_AGENT_DISALLOWED = List.of("Agent");
    public static final List<String> CUSTOM_AGENT_DISALLOWED = List.of();
    public static final List<String> ASYNC_ALLOWED = List.of(
        "read_file", "write_file", "edit_file",
        "search_file", "search_content", "execute_command", "install_skill");
    public static final long AUTO_BACKGROUND_MS = 120_000L;

    private SubAgentConstants() {}
}
