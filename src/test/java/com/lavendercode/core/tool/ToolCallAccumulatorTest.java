package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ToolCallAccumulatorTest {
    @Test
    void singleFragments() {
        var a = new ToolCallAccumulator();
        a.start("id1", "read");
        a.append("id1", "{\"p");
        a.append("id1", "\":\"v\"}");
        var c = a.complete("id1");
        assertThat(c.parameters()).containsEntry("p", "v");
        assertThat(c.name()).isEqualTo("read");
    }

    @Test
    void interleaved() {
        var a = new ToolCallAccumulator();
        a.start("c1", "t1");
        a.start("c2", "t2");
        a.append("c1", "{\"x\":1}");
        a.append("c2", "{\"y\":2}");
        var r2 = a.complete("c2");
        var r1 = a.complete("c1");
        assertThat(r2.parameters()).containsEntry("y", 2);
        assertThat(r1.parameters()).containsEntry("x", 1);
    }

    @Test
    void parseError() {
        var a = new ToolCallAccumulator();
        a.start("b", "t");
        a.append("b", "!!!");
        var c = a.complete("b");
        assertThat(c.parseError()).isNotNull();
    }

    @Test
    void nullForUnknown() {
        assertThat(new ToolCallAccumulator().complete("x")).isNull();
    }

    @Test
    void clear() {
        var a = new ToolCallAccumulator();
        a.start("a", "t");
        a.clear();
        assertThat(a.isEmpty()).isTrue();
    }
}
