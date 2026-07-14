package com.lavendercode.core.permission;

import java.util.regex.Pattern;

public final class PatternMatcher {
    private final MatchType type;
    private final Pattern compiledRegex;
    private final PatternMatcher innerMatcher;

    public PatternMatcher(MatchType type) {
        this.type = type;
        if (type instanceof MatchType.Regex r) {
            try {
                this.compiledRegex = Pattern.compile(r.value());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid regex: " + r.value(), e);
            }
        } else {
            this.compiledRegex = null;
        }
        if (type instanceof MatchType.Not n) {
            this.innerMatcher = new PatternMatcher(n.inner());
        } else {
            this.innerMatcher = null;
        }
    }

    public boolean matches(String input) {
        if (input == null) input = "";
        return switch (type) {
            case MatchType.Exact e -> input.equals(e.value());
            case MatchType.Glob g -> GlobMatcher.matches(input, g.value(), false);
            case MatchType.Regex r -> compiledRegex.matcher(input).find();
            case MatchType.Not n -> !innerMatcher.matches(input);
        };
    }

    public MatchType type() { return type; }

    public static PatternMatcher parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new PatternMatcher(new MatchType.Glob(""));
        }
        return new PatternMatcher(parseType(raw));
    }

    private static MatchType parseType(String raw) {
        char first = raw.charAt(0);
        return switch (first) {
            case '=' -> new MatchType.Exact(raw.substring(1));
            case '~' -> new MatchType.Regex(raw.substring(1));
            case '!' -> new MatchType.Not(parseType(raw.substring(1)));
            default -> new MatchType.Glob(raw);
        };
    }
}
