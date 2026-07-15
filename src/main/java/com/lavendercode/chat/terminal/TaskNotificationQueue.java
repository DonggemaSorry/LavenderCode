package com.lavendercode.chat.terminal;

import com.lavendercode.core.task.BackgroundTask;
import com.lavendercode.core.task.TaskNotificationFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TaskNotificationQueue {

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void offer(BackgroundTask task) {
        if (task != null && task.isTerminated()) {
            queue.offer(TaskNotificationFormatter.format(task));
        }
    }

    public List<String> drain() {
        List<String> drained = new ArrayList<>();
        String item;
        while ((item = queue.poll()) != null) {
            drained.add(item);
        }
        return drained;
    }
}
