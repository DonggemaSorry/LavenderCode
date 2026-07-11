package com.lavendercode.core.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvExpander {
    private static final Pattern VAR = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private EnvExpander() {}

    public static Map<String, String> expandValues(
            Map<String, String> raw, String contextLabel, Function<String, String> envLookup) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            out.put(entry.getKey(), expandString(entry.getValue(), contextLabel, envLookup));
        }
        return Map.copyOf(out);
    }

    public static Map<String, String> expandValues(Map<String, String> raw, String contextLabel) {
        return expandValues(raw, contextLabel, System::getenv);
    }

    static String expandString(String value, String contextLabel, Function<String, String> envLookup) {
        if (value == null) {
            return "";
        }
        Matcher matcher = VAR.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String var = matcher.group(1);
            String resolved = envLookup.apply(var);
            if (resolved == null) {
                System.err.println(
                    "WARN: MCP server '" + contextLabel + "': env var '${" + var + "}' undefined");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
