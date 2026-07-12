package com.lavendercode.core.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummaryResponseParser {
    private static final Pattern SUMMARY = Pattern.compile("<summary>(.*?)</summary>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private SummaryResponseParser() {}

    public static String extractSummary(String raw) {
        if (raw == null) return "";
        Matcher m = SUMMARY.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return raw.trim();
    }
}
