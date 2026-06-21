package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputAreaLayoutTest {

    @Test
    void shouldStartWithMinimumEditRows() {
        var layout = new InputAreaLayout();
        layout.update(24, InputAreaLayout.MIN_EDIT_ROWS);

        assertThat(layout.editRows()).isEqualTo(1);
        assertThat(layout.separatorBotRow()).isEqualTo(23);
        assertThat(layout.inputLastRow()).isEqualTo(22);
        assertThat(layout.inputFirstRow()).isEqualTo(22);
        assertThat(layout.separatorTopRow()).isEqualTo(21);
    }

    @Test
    void shouldGrowWithRequestedEditRows() {
        var layout = new InputAreaLayout();
        layout.update(24, 5);

        assertThat(layout.editRows()).isEqualTo(5);
        assertThat(layout.inputFirstRow()).isEqualTo(18);
        assertThat(layout.inputLastRow()).isEqualTo(22);
        assertThat(layout.separatorTopRow()).isEqualTo(17);
    }

    @Test
    void shouldCapEditRowsForSmallTerminal() {
        assertThat(InputAreaLayout.maxEditRows(10)).isGreaterThanOrEqualTo(InputAreaLayout.MIN_EDIT_ROWS);
        var layout = new InputAreaLayout();
        layout.update(10, 100);
        assertThat(layout.editRows()).isLessThanOrEqualTo(InputAreaLayout.MAX_EDIT_ROWS_CAP);
    }

    @Test
    void viewportShouldStopAboveTopBorder() {
        var layout = new InputAreaLayout();
        layout.update(24, 3);
        int viewportHeight = layout.separatorTopRow() - 1;
        assertThat(viewportHeight).isEqualTo(18);
        assertThat(layout.separatorTopRow()).isEqualTo(19);
    }
}
