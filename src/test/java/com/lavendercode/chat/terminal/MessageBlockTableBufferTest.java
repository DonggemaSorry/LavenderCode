package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证表格块缓冲到闭合后再整体渲染为对齐框线表格。 */
class MessageBlockTableBufferTest {

    private static String lineText(MessageBlock block, int index) {
        StringBuilder sb = new StringBuilder();
        block.allLines().get(index).segments().forEach(s -> sb.append(s.toString()));
        return sb.toString();
    }

    private static String allText(MessageBlock block) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < block.allLines().size(); i++) {
            sb.append(lineText(block, i)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void bufferedRowsShouldNotEmitLinesUntilClosed() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n| 1 | 2 |\n", 80);

        assertThat(block.lineCount()).isZero();
    }

    @Test
    void closePendingTableShouldRenderAlignedTable() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n| 1 | 2 |\n", 80);

        block.closePendingTable(80);

        assertThat(block.lineCount()).isGreaterThanOrEqualTo(3);
        assertThat(allText(block))
            .contains("\u2502")   // 框线竖线
            .contains("a").contains("1")
            .doesNotContain("---"); // 分隔行被消费
    }

    @Test
    void nonTableLineShouldFlushBuffer() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\nplain\n", 80);

        assertThat(allText(block)).contains("\u2502").contains("plain");
    }

    @Test
    void blankLineShouldEndTable() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n\nnext", 80);

        assertThat(allText(block)).contains("\u2502").contains("next");
    }

    @Test
    void invalidTableShouldPassThroughRaw() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| lonely row |\nplain\n", 80);

        assertThat(lineText(block, 0)).isEqualTo("| lonely row |");
        assertThat(lineText(block, 1)).isEqualTo("plain");
    }

    @Test
    void partialTableRowShouldStayVisibleAsTail() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n| strea", 80);

        // 已闭合的两行进入缓冲，未闭合尾行保持原文预览
        assertThat(block.lineCount()).isEqualTo(1);
        assertThat(lineText(block, 0)).isEqualTo("| strea");
    }

    @Test
    void thinkingShouldFlushPendingTable() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n", 80);

        block.appendThinking("thinking", 80);

        assertThat(allText(block)).contains("\u2502").contains("thinking");
    }

    @Test
    void toolRowShouldFlushPendingTable() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n", 80);

        block.appendToolRow("read_file", "x.txt", "running", null, true, 80);

        assertThat(allText(block)).contains("\u2502").contains("Read");
    }

    @Test
    void fenceShouldFlushPendingTable() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n```\ncode\n```\n", 80);

        String all = allText(block);
        assertThat(all).contains("\u2502").contains("code");
    }

    @Test
    void reflowShouldRebuildBufferConsistently() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("| a | b |\n| --- | --- |\n| 1 | 2 |\nplain\n", 80);
        String before = allText(block);

        block.reflow(80);

        assertThat(allText(block)).isEqualTo(before);
    }
}
