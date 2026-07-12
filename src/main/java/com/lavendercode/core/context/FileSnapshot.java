package com.lavendercode.core.context;

import java.time.Instant;

public record FileSnapshot(String path, Instant readAt, String content) {}
