package com.lavendercode.chat.session;

import com.lavendercode.core.provider.Message;

import java.util.List;

public record RestoreResult(
    List<Message> messages,
    boolean compacted,
    String timeSpanReminderOrNull
) {
}
