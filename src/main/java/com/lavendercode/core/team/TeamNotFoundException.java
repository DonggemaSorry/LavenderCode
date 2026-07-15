package com.lavendercode.core.team;

public final class TeamNotFoundException extends RuntimeException {
    public TeamNotFoundException(String name) {
        super("团队不存在: " + name);
    }
}
