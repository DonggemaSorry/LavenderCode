package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BackendType {
    TMUX("tmux"),
    ITERM2("iterm2"),
    IN_PROCESS("in-process");

    private final String wire;

    BackendType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wireValue() {
        return wire;
    }

    @JsonCreator
    public static BackendType fromWire(String w) {
        if (w == null) {
            throw new IllegalArgumentException("未知 backend: null");
        }
        for (BackendType t : values()) {
            if (t.wire.equals(w)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知 backend: " + w);
    }
}
