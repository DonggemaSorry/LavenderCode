package com.lavendercode.core.context;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultContextManagerTest {
    @TempDir
    Path projectRoot;

    private DefaultContextManager buildManager(SessionManager sm,
                                               Layer1Offloader layer1,
                                               CompactionService compaction,
                                               TokenEstimator estimator,
                                               int contextWindow) {
        return new DefaultContextManager(
            sm, layer1, compaction, estimator, new FileReadTracker(),
            contextWindow, new AutoCompactCircuitBreaker());
    }

    @Test
    void manageContextRunsLayer1BeforeThresholdCheck() {
        SessionManager sm = new InMemorySessionManager();
        sm.addUserMessage("short");
        Layer1Offloader layer1 = mock(Layer1Offloader.class);
        when(layer1.offloadAndSnip()).thenReturn(0);
        CompactionService compaction = mock(CompactionService.class);
        TokenEstimator estimator = new TokenEstimator();

        DefaultContextManager mgr = buildManager(sm, layer1, compaction, estimator, 128_000);
        ManageOutcome outcome = mgr.manageContext(CompactTrigger.AUTO, List.of());

        verify(layer1).offloadAndSnip();
        verify(compaction, never()).compact(any(), anyList());
        assertThat(outcome).isEqualTo(ManageOutcome.LAYER1_ONLY);
    }

    @Test
    void manageContextTriggersCompactionWhenOverThreshold() {
        SessionManager sm = new InMemorySessionManager();
        sm.addUserMessage("x".repeat(100_000));
        Layer1Offloader layer1 = mock(Layer1Offloader.class);
        when(layer1.offloadAndSnip()).thenReturn(0);
        CompactionService compaction = mock(CompactionService.class);
        when(compaction.compact(eq(CompactTrigger.AUTO), anyList()))
            .thenReturn(CompactResult.ok(90_000, 40_000));
        TokenEstimator estimator = new TokenEstimator();
        estimator.replaceAnchor(90_000, 0, 0, 0);

        DefaultContextManager mgr = buildManager(sm, layer1, compaction, estimator, 100_000);
        ManageOutcome outcome = mgr.manageContext(CompactTrigger.AUTO, List.of());

        verify(compaction).compact(CompactTrigger.AUTO, List.of());
        assertThat(outcome).isEqualTo(ManageOutcome.COMPACTED);
    }

    @Test
    void runCompactionDelegatesToService() {
        SessionManager sm = new InMemorySessionManager();
        Layer1Offloader layer1 = mock(Layer1Offloader.class);
        CompactionService compaction = mock(CompactionService.class);
        when(compaction.compact(CompactTrigger.MANUAL, List.of()))
            .thenReturn(CompactResult.ok(50_000, 20_000));
        TokenEstimator estimator = new TokenEstimator();

        DefaultContextManager mgr = buildManager(sm, layer1, compaction, estimator, 128_000);
        CompactResult result = mgr.runCompaction(CompactTrigger.MANUAL, List.of());

        assertThat(result.success()).isTrue();
        assertThat(result.tokensAfter()).isEqualTo(20_000);
    }

    @Test
    void nonAutoManageContextReturnsUnchanged() {
        SessionManager sm = new InMemorySessionManager();
        Layer1Offloader layer1 = mock(Layer1Offloader.class);
        CompactionService compaction = mock(CompactionService.class);
        TokenEstimator estimator = new TokenEstimator();
        DefaultContextManager mgr = buildManager(sm, layer1, compaction, estimator, 128_000);

        assertThat(mgr.manageContext(CompactTrigger.MANUAL, List.of())).isEqualTo(ManageOutcome.UNCHANGED);
        verifyNoInteractions(layer1);
    }
}
