package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBlockIncrementalWrapTest {

    /** 提取所有行的「文本|样式」序列，用于逐行等价比较。 */
    private static List<AbstractMap.SimpleEntry<String, AttributedStyle>> snapshot(MessageBlock b) {
        return b.allLines().stream()
            .flatMap(l -> l.segments().stream()
                .map(s -> new AbstractMap.SimpleEntry<>(s.toString(), s.styleAt(0))))
            .toList();
    }

    @Test
    void chunkedAppendShouldMatchSingleAppend() {
        String full = "Hello world this is a wrapping test\nSecond line here\n```java\ncode();\n```\nafter fence text";
        MessageBlock a = new MessageBlock(Role.ASSISTANT);
        a.append(full, 20);

        MessageBlock b = new MessageBlock(Role.ASSISTANT);
        for (String chunk : List.of("Hello wor", "ld this is a wrapp", "ing test\nSec",
                "ond line here\n```ja", "va\ncode();", "\n```\nafter fence", " text")) {
            b.append(chunk, 20);
        }

        assertThat(snapshot(b)).isEqualTo(snapshot(a));
    }

    @Test
    void stableLinesShouldNotBeRebuilt() {
        MessageBlock b = new MessageBlock(Role.ASSISTANT);
        b.append("stable line\npartial", 80);
        RenderedLine stable = b.allLines().get(0);

        b.append(" tail grows", 80);

        assertThat(b.allLines().get(0)).isSameAs(stable);
    }

    @Test
    void fenceStateShouldSurviveAcrossChunks() {
        MessageBlock b = new MessageBlock(Role.ASSISTANT);
        b.append("```ja", 80);
        b.append("va\ncode line\n```\nplain", 80);

        MessageBlock a = new MessageBlock(Role.ASSISTANT);
        a.append("```java\ncode line\n```\nplain", 80);

        assertThat(snapshot(b)).isEqualTo(snapshot(a));
    }

    @Test
    void reflowThenAppendShouldStayConsistent() {
        MessageBlock b = new MessageBlock(Role.ASSISTANT);
        b.append("1234567890", 10);
        b.reflow(5);
        b.append("ABCDE", 5);

        MessageBlock a = new MessageBlock(Role.ASSISTANT);
        a.append("1234567890ABCDE", 5);

        assertThat(snapshot(b)).isEqualTo(snapshot(a));
    }

    @Test
    void unclosedFenceShouldSurviveAcrossThinkingSegment() {
        MessageBlock b = new MessageBlock(Role.ASSISTANT);
        b.append("```python\ncode", 80);
        b.appendThinking("thinking", 80);
        b.append("\nmore code", 80);

        MessageBlock a = new MessageBlock(Role.ASSISTANT);
        a.append("```python\ncode", 80);
        a.appendThinking("thinking", 80);
        a.append("\nmore code", 80);

        // 两次 append 产生相同结果（自洽性），且 "more code" 行应为代码块样式
        assertThat(snapshot(b)).isEqualTo(snapshot(a));
        var snap = snapshot(b);
        // 找到 "more code" 行，验证其样式为代码块背景色
        var moreCodeEntry = snap.stream()
            .filter(e -> e.getKey().contains("more code"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("'more code' line not found"));
        assertThat(moreCodeEntry.getValue().toString()).contains("48;2;40;44;52"); // 代码块背景色
    }
}
