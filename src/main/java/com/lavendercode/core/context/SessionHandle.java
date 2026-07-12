package com.lavendercode.core.context;

import java.io.IOException;
import java.nio.file.Path;

public final class SessionHandle {
    private final Path projectRoot;
    private volatile String sessionId;
    private volatile SessionPaths paths;
    private volatile ContextManager contextManager;

    public SessionHandle(Path projectRoot, String sessionId, SessionPaths paths, ContextManager contextManager) {
        this.projectRoot = projectRoot;
        this.sessionId = sessionId;
        this.paths = paths;
        this.contextManager = contextManager;
    }

    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void rebind(String newSessionId) throws IOException {
        SessionPaths next = new SessionPaths(projectRoot, newSessionId);
        next.ensureDirectories();
        this.sessionId = newSessionId;
        this.paths = next;
    }

    public String sessionId() {
        return sessionId;
    }

    public SessionPaths paths() {
        return paths;
    }

    public ContextManager contextManager() {
        return contextManager;
    }

    public Path projectRoot() {
        return projectRoot;
    }
}
