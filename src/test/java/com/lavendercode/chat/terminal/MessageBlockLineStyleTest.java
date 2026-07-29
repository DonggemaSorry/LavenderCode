package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证行闭合时的逐行即时 Markdown 着色（原生滚动模式）。 */
class MessageBlockLineStyleTest {

    private static String lineText(MessageBlock block, int index) {
        StringBuilder sb = new StringBuilder();
        block.allLines().get(index).segments().forEach(s -> sb.append(s.toString()));
        return sb.toString();
    }

    @Test
    void closedHeadingLineShouldStripMarkers() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("## 核心架构\nnext", 80);

        assertThat(lineText(block, 0)).isEqualTo("核心架构");
    }

    @Test
    void closedBoldLineShouldStripMarkers() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("**QKV** 机制\nnext", 80);

        assertThat(lineText(block, 0)).isEqualTo("QKV 机制");
    }

    @Test
    void thematicBreakShouldRenderAsRule() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("---\nnext", 80);

        assertThat(lineText(block, 0)).contains("\u2500").doesNotContain("---");
    }

    @Test
    void fenceInteriorShouldStayLiteral() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("```\n# not a heading\n```\n", 80);

        assertThat(lineText(block, 1)).isEqualTo("# not a heading");
    }

    @Test
    void blankClosedLineShouldBePreserved() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("a\n\nb", 80);

        assertThat(block.lineCount()).isEqualTo(3);
        assertThat(lineText(block, 1)).isEmpty();
    }

    @Test
    void partialTailLineShouldStayRawUntilClosed() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("## streaming headi", 80);

        // 未闭合尾行保持原文，等待 '\n' 到达后才着色
        assertThat(lineText(block, 0)).isEqualTo("## streaming headi");

        block.append("ng\n", 80);
        assertThat(lineText(block, 0)).isEqualTo("streaming heading");
    }
}
