package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReplacementLedgerTest {
    @Test
    void replacementStringIsStableReference() {
        ReplacementLedger ledger = new ReplacementLedger();
        String preview = "PREVIEW-1";
        ledger.recordReplacement("id1", preview);
        assertThat(ledger.getReplacement("id1")).isSameAs(preview);
        ledger.recordReplacement("id1", "OTHER");
        assertThat(ledger.getReplacement("id1")).isSameAs(preview);
    }

    @Test
    void recordKeepOriginalMarksSeenWithoutReplacement() {
        ReplacementLedger ledger = new ReplacementLedger();
        ledger.recordKeepOriginal("id2");
        assertThat(ledger.isSeen("id2")).isTrue();
        assertThat(ledger.isReplaced("id2")).isFalse();
    }

    @Test
    void concurrentAccessHasNoRace() throws Exception {
        ReplacementLedger ledger = new ReplacementLedger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int t = 0; t < 50; t++) {
                final String id = "id-" + (t % 5);
                pool.submit(() -> ledger.recordReplacement(id, "p-" + id));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 5; i++) {
                assertThat(ledger.getReplacement("id-" + i)).isEqualTo("p-id-" + i);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
