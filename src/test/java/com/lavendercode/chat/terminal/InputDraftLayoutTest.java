package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputDraftLayoutTest {

    @Test
    void emptyDraftShouldShowPromptOnBottomRow() {
        List<InputDraftLayout.Line> lines = InputDraftLayout.format("", 0, 80, 3);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().prefix()).isEqualTo("> ");
        assertThat(lines.getFirst().showCursor()).isTrue();
    }

    @Test
    void shouldPlaceCursorInTypedText() {
        List<InputDraftLayout.Line> lines = InputDraftLayout.format("hi", 2, 80, 3);
        assertThat(lines.getFirst().text()).isEqualTo("hi");
        assertThat(lines.getFirst().showCursor()).isTrue();
        assertThat(lines.getFirst().cursorCol()).isEqualTo(2);
    }

    @Test
    void shouldWrapLongLineAcrossDisplayRows() {
        String longLine = "a".repeat(120);
        List<InputDraftLayout.Line> lines = InputDraftLayout.format(longLine, 60, 80, 3);
        assertThat(lines.size()).isLessThanOrEqualTo(3);
        assertThat(lines.stream().map(InputDraftLayout.Line::text).reduce("", String::concat).length())
            .isGreaterThan(80);
    }

    @Test
    void shouldGrowDesiredEditRowsWithContent() {
        String multi = "line1\nline2\nline3\nline4";
        int rows = InputDraftLayout.desiredEditRows(multi, 80, 24);
        assertThat(rows).isEqualTo(7);
    }

    @Test
    void shouldCapDesiredEditRowsAtTerminalMaximum() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("line").append(i).append('\n');
        }
        int rows = InputDraftLayout.desiredEditRows(sb.toString(), 80, 24);
        assertThat(rows).isEqualTo(InputAreaLayout.maxEditRows(24));
    }

    @Test
    void shouldScrollWindowToKeepCursorVisible() {
        List<InputDraftLayout.Line> lines = InputDraftLayout.format("a\nb\nc\nd\ne", 9, 80, 3);
        assertThat(lines).hasSize(3);
        assertThat(lines.getLast().text()).isEqualTo("e");
        assertThat(lines.getLast().showCursor()).isTrue();
    }

    @Test
    void shouldAnchorVisibleLinesToBottomWhenMoreThanMaxRows() {
        List<InputDraftLayout.Line> lines = InputDraftLayout.format("a\nb\nc\nd", 7, 80, 3);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).text()).isEqualTo("c");
        assertThat(lines.get(2).text()).isEqualTo("d");
    }
}
