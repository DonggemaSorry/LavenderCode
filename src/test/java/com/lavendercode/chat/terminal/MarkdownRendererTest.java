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
}
