package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import org.jline.utils.AttributedStyle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    // Bit constants for JLine AttributedStyle
    private static final long BOLD_BIT = 1L;       // F_BOLD
    private static final long ITALIC_BIT = 4L;      // F_ITALIC

    private static boolean isBold(AttributedStyle style) {
        return (style.getStyle() & BOLD_BIT) != 0 && (style.getMask() & BOLD_BIT) != 0;
    }

    private static boolean isItalic(AttributedStyle style) {
        return (style.getStyle() & ITALIC_BIT) != 0 && (style.getMask() & ITALIC_BIT) != 0;
    }

    @Test
    void shouldRenderPlainText() {
        List<RenderedLine> result = MarkdownRenderer.render("hello world", 80);
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldRenderBoldText() {
        List<RenderedLine> result = MarkdownRenderer.render("**bold** text", 80);
        assertThat(result).hasSize(1);
        assertThat(isBold(result.get(0).segments().get(0).styleAt(0))).isTrue();
    }

    @Test
    void shouldRenderItalicText() {
        List<RenderedLine> result = MarkdownRenderer.render("*italic* text", 80);
        assertThat(result).hasSize(1);
        assertThat(isItalic(result.get(0).segments().get(0).styleAt(0))).isTrue();
    }

    @Test
    void shouldRenderCodeBlock() {
        String md = """
            ```java
            System.out.println("hi");
            ```""";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldRenderHeadings() {
        List<RenderedLine> result = MarkdownRenderer.render("# Title", 80);
        assertThat(result).hasSize(1);
        assertThat(isBold(result.get(0).segments().get(0).styleAt(0))).isTrue();
    }

    @Test
    void shouldRenderBulletList() {
        String md = """
            - item 1
            - item 2""";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result).hasSize(2);
        // bullet list items should contain bullet marker
        assertThat(result.get(0).segments()).anyMatch(
            s -> s.toString().contains("\u2022")
        );
    }

    @Test
    void shouldWrapAtWidth() {
        String longText = "a".repeat(200);
        List<RenderedLine> result = MarkdownRenderer.render(longText, 40);
        assertThat(result.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void shouldHandleEmptyInput() {
        List<RenderedLine> result = MarkdownRenderer.render("", 80);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRenderBasicTable() {
        String md = "| Name | Age |\n|------|-----|\n| Alice | 30 |\n| Bob | 25 |";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        // Expect: header + separator + 2 body rows + bottom border = 5 lines
        assertThat(result.size()).isGreaterThanOrEqualTo(5);

        // Join all segments without newlines to check content
        String allText = joinPlainText(result);
        assertThat(allText).contains("\u2502"); // │
        assertThat(allText).contains("\u2500"); // ─
        assertThat(allText).contains("Alice");
        assertThat(allText).contains("Bob");
    }

    @Test
    void shouldRenderTableWithAlignment() {
        String md = "| Left | Center | Right |\n|:-----|:------:|------:|\n| a | b | c |";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result.size()).isGreaterThanOrEqualTo(4);

        String allText = joinPlainText(result);
        assertThat(allText).contains("Left");
        assertThat(allText).contains("Center");
        assertThat(allText).contains("Right");
    }

    @Test
    void shouldRenderTableWithCJKCharacters() {
        String md = "| 模型 | 说明 |\n|------|------|\n| DeepSeek | 开源 |\n| Qwen | 中文强 |";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result.size()).isGreaterThanOrEqualTo(5);

        String allText = joinPlainText(result);
        assertThat(allText).contains("DeepSeek");
        assertThat(allText).contains("\u6A21\u578B"); // 模型
    }

    @Test
    void shouldRenderTableWithBodyOnly() {
        String md = "| a | b |\n|---|---|\n| c | d |";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result.size()).isGreaterThanOrEqualTo(2);
    }

    /** Join all segment text from rendered lines without line breaks. */
    private static String joinPlainText(List<RenderedLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (RenderedLine line : lines) {
            for (var seg : line.segments()) {
                sb.append(seg.toString());
            }
        }
        return sb.toString();
    }
}
