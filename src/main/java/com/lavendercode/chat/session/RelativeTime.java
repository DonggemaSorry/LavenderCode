package com.lavendercode.chat.session;

import java.time.Duration;
import java.time.Instant;

public final class RelativeTime {
    private RelativeTime() {
    }

    public static String format(Instant then, Instant now) {
        Duration elapsed = Duration.between(then, now);
        if (elapsed.isNegative() || elapsed.toSeconds() < 60) {
            return "just now";
        }
        long minutes = elapsed.toMinutes();
        if (minutes < 60) {
            return plural(minutes, "minute");
        }
        long hours = elapsed.toHours();
        if (hours < 24) {
            return plural(hours, "hour");
        }
        long days = elapsed.toDays();
        if (days < 30) {
            return plural(days, "day");
        }
        long months = days / 30;
        if (months < 12) {
            return plural(months, "month");
        }
        return plural(days / 365, "year");
    }

    private static String plural(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s") + " ago";
    }
}
