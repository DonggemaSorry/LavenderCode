package com.lavendercode.core.context;

public final class ContextConstants {
    private ContextConstants() {}

    public static final int SINGLE_TOOL_RESULT_BYTES = 50_000;
    public static final int ROUND_AGGREGATE_BYTES = 200_000;
    public static final int PREVIEW_MAX_LINES = 20;
    public static final int PREVIEW_MAX_BYTES = 2_048;
    public static final double ESTIMATE_CHARS_PER_TOKEN = 3.5;
    public static final int SUMMARY_OUTPUT_RESERVE = 20_000;
    public static final int AUTO_SAFETY_MARGIN = 13_000;
    public static final int MANUAL_SAFETY_MARGIN = 3_000;
    public static final int RECENT_TAIL_MIN_TOKENS = 10_000;
    public static final int RECENT_TAIL_MIN_MESSAGES = 5;
    public static final int MAX_FILE_SNAPSHOTS = 5;
    public static final int FILE_SNAPSHOT_MAX_TOKENS = 5_000;
    public static final int AUTO_COMPACT_FAILURE_LIMIT = 3;
    public static final int PTL_DIRECT_RETRY_LIMIT = 3;
    public static final double PTL_DROP_RATIO = 0.2;
    public static final String READ_FILE_TOOL_NAME = "read_file";
}
