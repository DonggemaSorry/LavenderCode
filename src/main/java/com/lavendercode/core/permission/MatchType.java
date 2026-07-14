package com.lavendercode.core.permission;

public sealed interface MatchType {
    record Exact(String value) implements MatchType {}
    record Glob(String value) implements MatchType {}
    record Regex(String value) implements MatchType {}
    record Not(MatchType inner) implements MatchType {}
}
