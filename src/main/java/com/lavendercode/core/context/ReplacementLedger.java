package com.lavendercode.core.context;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ReplacementLedger {
    private final Set<String> seenIds = new HashSet<>();
    private final Map<String, String> replacements = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public boolean isSeen(String toolCallId) {
        lock.lock();
        try {
            return seenIds.contains(toolCallId);
        } finally {
            lock.unlock();
        }
    }

    public boolean isReplaced(String toolCallId) {
        lock.lock();
        try {
            return replacements.containsKey(toolCallId);
        } finally {
            lock.unlock();
        }
    }

    public String getReplacement(String toolCallId) {
        lock.lock();
        try {
            return replacements.get(toolCallId);
        } finally {
            lock.unlock();
        }
    }

    public void recordKeepOriginal(String toolCallId) {
        lock.lock();
        try {
            if (seenIds.contains(toolCallId)) return;
            seenIds.add(toolCallId);
        } finally {
            lock.unlock();
        }
    }

    public void recordReplacement(String toolCallId, String preview) {
        lock.lock();
        try {
            if (seenIds.contains(toolCallId)) return;
            seenIds.add(toolCallId);
            replacements.put(toolCallId, preview);
        } finally {
            lock.unlock();
        }
    }
}
