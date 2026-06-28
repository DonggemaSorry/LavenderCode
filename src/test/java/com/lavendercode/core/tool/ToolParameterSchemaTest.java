package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolParameterSchemaTest {
    @Test
    void shouldCreateWithRequiredParams() {
        var s = new ToolParameterSchema("object",
            Map.of("path", new ToolParameterSchema.PropertyDef("string", "路径", null, null)),
            List.of("path"));
        assertThat(s.type()).isEqualTo("object");
        assertThat(s.required()).containsExactly("path");
    }

    @Test
    void shouldSupportEnumAndItems() {
        var pd = new ToolParameterSchema.PropertyDef("string", "颜色", List.of("r", "g"), null);
        assertThat(pd.enumValues()).contains("r", "g");
    }

    @Test
    void emptyRequiredIsAllowed() {
        var s = new ToolParameterSchema("object", Map.of(), List.of());
        assertThat(s.required()).isEmpty();
    }
}
