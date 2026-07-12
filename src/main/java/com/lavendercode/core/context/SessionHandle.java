package com.lavendercode.core.context;

import java.io.IOException;
import java.nio.file.Path;

public final class SessionHandle {
    private record Binding(String sessionId, SessionPaths paths) {}

    private final Path projectRoot;
    private volatile Binding binding;
    private volatile ContextManager contextManager;

    public SessionHandle(Path projectRoot, String sessionId, SessionPaths paths, ContextManager contextManager) {
        this.projectRoot = projectRoot;
        this.binding = new Binding(sessionId, paths);
        this.contextManager = contextManager;
    }

    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void rebind(String newSessionId) throws IOException {
        SessionPaths next = new SessionPaths(projectRoot, newSessionId);
        next.ensureDirectories();
        this.binding = new Binding(newSessionId, next);
    }

    public String sessionId() {
        return binding.sessionId();
    }

    public SessionPaths paths() {
        return binding.paths();
    }

    public ContextManager contextManager() {
        return contextManager;
    }

    public Path projectRoot() {
        return projectRoot;
    }
}
