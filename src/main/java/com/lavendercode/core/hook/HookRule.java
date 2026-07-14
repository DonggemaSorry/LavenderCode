package com.lavendercode.core.hook;

import java.time.Duration;

public record HookRule(
    String name,
    HookEvent event,
    HookCondition condition,
    HookAction action,
    boolean onlyOnce,
    boolean async,
    Duration timeout
) {}
