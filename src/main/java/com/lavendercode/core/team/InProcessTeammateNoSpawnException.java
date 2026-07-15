package com.lavendercode.core.team;

public final class InProcessTeammateNoSpawnException extends RuntimeException {
    public InProcessTeammateNoSpawnException() {
        super("同进程队员不能再向 Team 派生成员");
    }
}