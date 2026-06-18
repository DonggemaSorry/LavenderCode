package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBlockTest {

    @Test
    void newBlockShouldHaveZeroLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        assertThat(block.lineCount()).isZero();
    }

    @Test
    void appendSingleLineShouldWrap() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.append("Hello", 80);
        assertThat(added).isEqualTo(1);
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void appendOverWidthShouldWrapToMultipleLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.append("1234567890", 5);
        assertThat(added).isEqualTo(2);
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void appendMultipleTimesShouldAccumulateCorrectly() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Hello", 80);
        int added = block.append(" World", 80);
        assertThat(added).isZero(); // still 1 line at width 80
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void newlinesShouldCreateMultipleLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.append("Line1\nLine2", 80);
        assertThat(added).isEqualTo(2);
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void codeBlockFencesShouldBeDetected() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Before\n```python\ncode\n```\nAfter", 80);
        assertThat(block.lineCount()).isEqualTo(5);
    }

    @Test
    void appendThinkingShouldCreateThinkingLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.appendThinking("Let me think...", 80);
        assertThat(added).isEqualTo(1);
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void reflowShouldHandleWidthChange() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("1234567890", 10);
        assertThat(block.lineCount()).isEqualTo(1);
        block.reflow(5);
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void markCompleteShouldSetFlag() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        assertThat(block.isComplete()).isFalse();
        block.markComplete();
        assertThat(block.isComplete()).isTrue();
    }

    @Test
    void shouldReturnAllLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("A\nB", 80);
        assertThat(block.allLines()).hasSize(2);
    }

    @Test
    void thinkingContentMixedShouldRetainOrder() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Answer:", 80);
        block.appendThinking("Let me think...", 80);
        block.append(" The result is 42.", 80);
        assertThat(block.lineCount()).isEqualTo(3);
    }
}
