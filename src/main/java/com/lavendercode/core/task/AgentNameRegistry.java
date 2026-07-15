package com.lavendercode.core.task;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public final class AgentNameRegistry {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, String> byName = new HashMap<>();
    private final Map<String, String> byId = new HashMap<>();

    public void register(String name, String agentId) {
        lock.lock();
        try {
            String oldId = byName.put(name, agentId);
            if (oldId != null) {
                byId.remove(oldId, name);
            }
            String oldName = byId.put(agentId, name);
            if (oldName != null && !oldName.equals(name)) {
                byName.remove(oldName, agentId);
            }
        } finally {
            lock.unlock();
        }
    }

    public void unregister(String name) {
        lock.lock();
        try {
            String id = byName.remove(name);
            if (id != null) {
                byId.remove(id, name);
            }
        } finally {
            lock.unlock();
        }
    }

    public Optional<String> resolve(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.empty();
        }
        lock.lock();
        try {
            if (byName.containsKey(nameOrId)) {
                return Optional.of(byName.get(nameOrId));
            }
            if (byId.containsKey(nameOrId)) {
                return Optional.of(nameOrId);
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public Optional<String> nameOf(String agentId) {
        lock.lock();
        try {
            return Optional.ofNullable(byId.get(agentId));
        } finally {
            lock.unlock();
        }
    }
}
