package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineMarkdownStylerTest {

    private static String joinedText(List<RenderedLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (RenderedLine line : lines) {
            line.segments().forEach(s -> sb.append(s.toString()));
        }
        return sb.toString();
    }

    @Test
    void headingMarkersShouldBeStripped() {
        List<RenderedLine> result = LineMarkdownStyler.style("## 核心架构", 80);

        assertThat(result).hasSize(1);
        assertThat(joinedText(result)).contains("核心架构").doesNotContain("##");
    }

    @Test
    void boldMarkersShouldBeStripped() {
        List<RenderedLine> result = LineMarkdownStyler.style("**QKV** 机制", 80);

        assertThat(joinedText(result)).isEqualTo("QKV 机制");
    }

    @Test
    void bulletShouldBePrettified() {
        List<RenderedLine> result = LineMarkdownStyler.style("- 让模型关注", 80);

        assertThat(joinedText(result)).contains("\u2022").contains("让模型关注");
    }

    @Test
    void thematicBreakShouldRenderAsRule() {
        List<RenderedLine> result = LineMarkdownStyler.style("---", 40);

        assertThat(result).hasSize(1);
        assertThat(joinedText(result)).contains("\u2500").doesNotContain("---");
    }

    @Test
    void tableRowShouldPassThroughUnchanged() {
        List<RenderedLine> result = LineMarkdownStyler.style("| 技术 | 作用 |", 80);

        assertThat(joinedText(result)).isEqualTo("| 技术 | 作用 |");
    }

    @Test
    void emptyLineShouldProduceOneBlankLine() {
        List<RenderedLine> result = LineMarkdownStyler.style("", 80);

        assertThat(result).hasSize(1);
        assertThat(joinedText(result)).isEmpty();
    }

    @Test
    void resultShouldNeverBeEmptyEvenForDroppedConstructs() {
        // 4-space indent parses as indented code block which flexmark walks to nothing
        List<RenderedLine> result = LineMarkdownStyler.style("    indented text", 80);

        assertThat(result).isNotEmpty();
        assertThat(joinedText(result)).contains("indented text");
    }

    @Test
    void longLineShouldWrapAtWidth() {
        List<RenderedLine> result = LineMarkdownStyler.style("aaaaaaaaaa", 5);

        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }
}
