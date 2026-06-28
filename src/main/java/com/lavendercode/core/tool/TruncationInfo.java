package com.lavendercode.core.tool;

/** Truncation metadata, reserving protocol space for future pagination capability. */
public record TruncationInfo(int totalCount, int displayedCount, int offset, int limit) {}
