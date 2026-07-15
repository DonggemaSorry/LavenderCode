package com.lavendercode.core.team;

public final class TeamHasActiveMembersException extends RuntimeException {
    public TeamHasActiveMembersException(String name) {
        super("团队仍有活跃成员，无法删除: " + name);
    }
}
