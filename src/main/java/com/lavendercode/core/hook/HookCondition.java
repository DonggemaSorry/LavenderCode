package com.lavendercode.core.hook;

import com.lavendercode.core.permission.MatchType;
import java.util.List;

public sealed interface HookCondition {
    record AllOf(List<Atom> atoms) implements HookCondition {}
    record AnyOf(List<Atom> atoms) implements HookCondition {}
    record Atom(String field, MatchType match) {}
}
