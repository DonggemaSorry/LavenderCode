package com.lavendercode.core.hook;

import java.util.List;

public record HookConfig(List<HookRule> rules, List<String> sources) {
    public HookConfig {
        rules = List.copyOf(rules);
        sources = List.copyOf(sources);
    }
}
