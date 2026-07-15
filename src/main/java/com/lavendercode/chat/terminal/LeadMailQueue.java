package com.lavendercode.chat.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Lead 邮箱更新队列，供 ReminderInjector / IDLE 自动续推消费。 */
public final class LeadMailQueue {
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

    public void offer(String reminderXml) {
        if (reminderXml != null && !reminderXml.isBlank()) {
            queue.offer(reminderXml);
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

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
