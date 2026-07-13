package com.lavendercode.core.memory;

public record MemoryAction(
    String action,
    String level,
    MemoryNoteType type,
    String title,
    String slug,
    String filename,
    String content
) {}
