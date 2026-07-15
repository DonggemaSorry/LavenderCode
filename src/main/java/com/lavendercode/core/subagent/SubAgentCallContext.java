package com.lavendercode.core.subagent;

public final class SubAgentCallContext {

    public enum Kind {
        MAIN,
        DEFINED,
        FORK
    }

    private static final ThreadLocal<Kind> CURRENT = new ThreadLocal<>();

    private SubAgentCallContext() {}

    public static Kind current() {
        Kind kind = CURRENT.get();
        return kind != null ? kind : Kind.MAIN;
    }

    public static <T> T run(Kind kind, java.util.concurrent.Callable<T> action) {
        Kind previous = CURRENT.get();
        CURRENT.set(kind);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
