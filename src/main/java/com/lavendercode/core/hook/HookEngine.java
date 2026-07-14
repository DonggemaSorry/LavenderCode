package com.lavendercode.core.hook;

import java.util.concurrent.atomic.AtomicBoolean;

public interface HookEngine {
    HookInterceptResult dispatch(HookEvent event, HookPayload payload, AtomicBoolean cancelFlag);
    void clearOnce();
    HookReminderQueue reminderQueue();
    HookConfig config();
}
