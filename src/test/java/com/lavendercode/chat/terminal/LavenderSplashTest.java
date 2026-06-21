package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LavenderSplashTest {

    @Test
    void displayWidthCountsCjkAsTwoColumns() {
        assertThat(LavenderSplash.displayWidth("等你，")).isEqualTo(6);
        assertThat(LavenderSplash.displayWidth("在薰衣草盛开的地方")).isEqualTo(18);
        assertThat(LavenderSplash.displayWidth("ab")).isEqualTo(2);
    }

    @Test
    void meadowSpanUsesOverlappingStep() {
        assertThat(LavenderSplash.meadowSpan(1)).isEqualTo(24);
        assertThat(LavenderSplash.meadowSpan(3)).isEqualTo(24 + 9 * 2);
    }

    @Test
    void computeMeadowFlowerCountFillsWideTerminals() {
        assertThat(LavenderSplash.computeMeadowFlowerCount(120)).isGreaterThanOrEqualTo(10);
        assertThat(LavenderSplash.computeMeadowFlowerCount(42)).isEqualTo(3);
        assertThat(LavenderSplash.computeMeadowFlowerCount(25)).isEqualTo(0);
    }

    @Test
    void composeMeadowBlendsOverlappingStems() {
        LavenderSplash.MeadowCanvas meadow = LavenderSplash.composeMeadow(120);
        assertThat(meadow).isNotNull();
        assertThat(meadow.height()).isGreaterThan(41);

        int purplePixels = 0;
        for (int[] row : meadow.pixels()) {
            for (int rgb : row) {
                if (rgb != 0) {
                    purplePixels++;
                }
            }
        }
        assertThat(purplePixels).isGreaterThan(24 * 41 * 3);
    }

    @Test
    void layoutKeepsBothTaglinesOnScreen() {
        LavenderSplash.MeadowCanvas meadow = LavenderSplash.composeMeadow(120);
        assertThat(meadow).isNotNull();

        for (int terminalRows : new int[] {24, 30, 40}) {
            LavenderSplash.SplashLayout layout = LavenderSplash.computeLayout(terminalRows, meadow);
            int meadowEndRow = layout.meadowStartRow() + layout.visibleMeadowHeight() - 1;

            assertThat(layout.ornamentRow()).isGreaterThan(meadowEndRow);
            assertThat(layout.firstTextRow()).isGreaterThan(layout.ornamentRow());
            assertThat(layout.secondTextRow()).isGreaterThan(layout.firstTextRow());
            assertThat(layout.secondTextRow()).isLessThan(terminalRows);
        }
    }

    @Test
    void clipToHeightKeepsTopOfMeadow() {
        LavenderSplash.MeadowCanvas meadow = LavenderSplash.composeMeadow(120);
        LavenderSplash.MeadowCanvas clipped = LavenderSplash.clipToHeight(meadow, 10);
        assertThat(clipped.height()).isEqualTo(10);
        assertThat(clipped.pixels()[0]).isEqualTo(meadow.pixels()[0]);
    }
}
