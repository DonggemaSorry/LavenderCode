package com.lavendercode.core.subagent;

import com.lavendercode.core.tool.ToolRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ToolFilter {

    private ToolFilter() {}

    public static Set<String> filter(AgentDefinition def, boolean fork, boolean background) {
        Set<String> all = ToolRegistry.export().stream()
            .map(d -> d.name())
            .collect(Collectors.toCollection(HashSet::new));

        if (!fork) {
            all.removeAll(SubAgentConstants.ALL_AGENT_DISALLOWED);
            all.removeAll(SubAgentConstants.CUSTOM_AGENT_DISALLOWED);
        }

        if (background) {
            Set<String> asyncBase = new HashSet<>(SubAgentConstants.ASYNC_ALLOWED);
            all.stream().filter(n -> n.startsWith("mcp_")).forEach(asyncBase::add);
            all.retainAll(asyncBase);
        }

        if (fork) {
            all.add("Agent");
        }

        if (def.disallowedTools() != null) {
            all.removeAll(def.disallowedTools());
        }

        if (def.tools() != null && !def.tools().isEmpty()) {
            all.retainAll(new HashSet<>(def.tools()));
        }

        return all;
    }

    public static List<com.lavendercode.core.tool.ToolDefinition> filterDefinitions(
            AgentDefinition def, boolean fork, boolean background) {
        Set<String> allowed = filter(def, fork, background);
        return ToolRegistry.export().stream()
            .filter(d -> allowed.contains(d.name()))
            .toList();
    }
}
