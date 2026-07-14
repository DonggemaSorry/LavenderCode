package com.lavendercode.core.hook;

import com.lavendercode.core.permission.PatternMatcher;
import java.util.Map;

public final class ConditionMatcher {
    private ConditionMatcher() {}

    public static boolean matches(HookCondition condition, HookPayload payload) {
        if (condition == null) return true;
        return switch (condition) {
            case HookCondition.AllOf all ->
                all.atoms().stream().allMatch(a -> matchAtom(a, payload));
            case HookCondition.AnyOf any ->
                any.atoms().stream().anyMatch(a -> matchAtom(a, payload));
        };
    }

    private static boolean matchAtom(HookCondition.Atom atom, HookPayload payload) {
        String fieldValue = extractField(atom.field(), payload);
        return new PatternMatcher(atom.match()).matches(fieldValue);
    }

    @SuppressWarnings("unchecked")
    public static String extractField(String path, HookPayload payload) {
        String[] parts = path.split("\\.");
        Object current = payload.fields();
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else {
                return "";
            }
            if (current == null) return "";
        }
        return current.toString();
    }
}
