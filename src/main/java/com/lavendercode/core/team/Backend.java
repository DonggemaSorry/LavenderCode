package com.lavendercode.core.team;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public interface Backend {
    BackendType type();

    SpawnResult spawn(SpawnRequest req) throws IOException;

    void wake(String paneId, String agentId) throws IOException;

    void kill(String paneId, String agentId) throws IOException;
}
