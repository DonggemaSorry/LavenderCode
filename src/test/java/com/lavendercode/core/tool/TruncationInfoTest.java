package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TruncationInfoTest {
    @Test
    void allFields() {
        var t = new TruncationInfo(5000, 2000, 0, 2000);
        assertThat(t.totalCount()).isEqualTo(5000);
        assertThat(t.displayedCount()).isEqualTo(2000);
        assertThat(t.offset()).isEqualTo(0);
        assertThat(t.limit()).isEqualTo(2000);
    }

    @Test
    void partialDisplay() {
        var t = new TruncationInfo(150, 100, 50, 200);
        assertThat(t.offset()).isEqualTo(50);
        assertThat(t.totalCount()).isEqualTo(150);
        assertThat(t.displayedCount()).isEqualTo(100);
        assertThat(t.limit()).isEqualTo(200);
    }
}
