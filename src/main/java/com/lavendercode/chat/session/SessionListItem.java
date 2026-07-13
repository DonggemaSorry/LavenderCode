package com.lavendercode.chat.session;

import java.nio.file.Path;

public record SessionListItem(
    String sessionId,
    String title,
    String relativeTime,
    String model,
    long sizeBytes,
    Path jsonlPath
) {
}
