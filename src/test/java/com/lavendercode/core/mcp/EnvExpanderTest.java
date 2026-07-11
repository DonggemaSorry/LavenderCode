package com.lavendercode.core.mcp;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class EnvExpanderTest {
    @Test
    void expandsDefinedVariable() {
        Map<String, String> out = EnvExpander.expandValues(
            Map.of("Authorization", "Bearer ${TOKEN}"),
            "test-server",
            key -> "TOKEN".equals(key) ? "secret" : null);
        assertThat(out.get("Authorization")).isEqualTo("Bearer secret");
    }

    @Test
    void undefinedVariableBecomesEmpty() {
        Map<String, String> out = EnvExpander.expandValues(
            Map.of("Authorization", "Bearer ${MISSING}"),
            "test-server",
            key -> null);
        assertThat(out.get("Authorization")).isEqualTo("Bearer ");
    }

    @Test
    void leavesNonPlaceholderValuesUntouched() {
        Map<String, String> out = EnvExpander.expandValues(
            Map.of("X", "plain"),
            "test-server",
            key -> "unused");
        assertThat(out.get("X")).isEqualTo("plain");
    }
}
