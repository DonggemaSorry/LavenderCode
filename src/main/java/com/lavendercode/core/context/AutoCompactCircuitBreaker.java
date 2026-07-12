package com.lavendercode.core.context;

import java.util.concurrent.locks.ReentrantLock;

public final class AutoCompactCircuitBreaker {
    private final ReentrantLock lock = new ReentrantLock();
    private int failureCount = 0;
    private boolean tripped = false;

    public boolean isTripped() {
        lock.lock();
        try {
            return tripped;
        } finally {
            lock.unlock();
        }
    }

    public void recordSuccess() {
        lock.lock();
        try {
            failureCount = 0;
            tripped = false;
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure() {
        lock.lock();
        try {
            failureCount++;
            if (failureCount >= ContextConstants.AUTO_COMPACT_FAILURE_LIMIT) {
                tripped = true;
            }
        } finally {
            lock.unlock();
        }
    }

    public int failureCount() {
        lock.lock();
        try {
            return failureCount;
        } finally {
            lock.unlock();
        }
    }
}
