package com.lavendercode.core.task;

import com.lavendercode.core.provider.Message;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public record BackgroundTask(
    String id,
    String name,
    TaskStatus status,
    Instant startTime,
    Instant endTime,
    String result,
    String error,
    AtomicBoolean cancelFlag,
    List<Message> conversation
) {
    public BackgroundTask(String id, String name, TaskStatus status, Instant startTime,
                          Instant endTime, String result, String error, AtomicBoolean cancelFlag) {
        this(id, name, status, startTime, endTime, result, error, cancelFlag, null);
    }

    public boolean isTerminated() {
        return status != TaskStatus.RUNNING;
    }
}
