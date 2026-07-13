package com.lavendercode.core.context;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.tool.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompactionServiceTest {
    @TempDir
    Path projectRoot;

    private static StreamEventIterator summaryIterator(String text) {
        StreamEventIterator iter = mock(StreamEventIterator.class);
        List<StreamEvent> events = List.of(
            new StreamEvent.ContentDelta(text),
            new StreamEvent.StreamComplete()
        );
        AtomicInteger hasNext = new AtomicInteger(0);
        AtomicInteger next = new AtomicInteger(0);
        when(iter.hasNext()).thenAnswer(inv -> hasNext.getAndIncrement() < events.size());
        when(iter.next()).thenAnswer(inv -> events.get(next.getAndIncrement()));
        return iter;
    }

    private static StreamEventIterator errorIterator(String message, int status) {
        StreamEventIterator iter = mock(StreamEventIterator.class);
        AtomicInteger calls = new AtomicInteger(0);
        when(iter.hasNext()).thenAnswer(inv -> calls.getAndIncrement() == 0);
        when(iter.next()).thenReturn(new StreamEvent.StreamError(message, status));
        return iter;
    }

    private CompactionService buildService(SessionManager sm, LlmProvider provider) {
        SessionPaths paths = new SessionPaths(projectRoot, "test-session");
        ReplacementLedger ledger = new ReplacementLedger();
        Layer1Offloader layer1 = new Layer1Offloader(sm, () -> paths, ledger);
        TokenEstimator estimator = new TokenEstimator();
        FileReadTracker tracker = new FileReadTracker();
        AutoCompactCircuitBreaker breaker = new AutoCompactCircuitBreaker();
        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4", "http://localhost", "key", null)),
            new Options());
        return new CompactionService(sm, provider, config, layer1, estimator, tracker, breaker, 128_000);
    }

    @Test
    void summaryRequestHasNoTools() {
        SessionManager sm = new InMemorySessionManager();
        sm.addUserMessage("hello");
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iter = summaryIterator(
            "<analysis>x</analysis><summary>## 1. Intent\nok</summary>");
        when(provider.streamChat(anyList(), any(), eq(List.of()))).thenReturn(iter);

        CompactionService service = buildService(sm, provider);
        CompactResult result = service.compact(CompactTrigger.MANUAL, List.of());

        assertThat(result.success()).isTrue();
        verify(provider).streamChat(anyList(), any(), eq(List.of()));
        assertThat(sm.getHistory().get(0).content()).contains("## Conversation Summary");
    }

    @Test
    void autoFailureTripsCircuitBreaker() {
        SessionManager sm = new InMemorySessionManager();
        for (int i = 0; i < 5; i++) {
            sm.addUserMessage("user-" + i);
        }
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.streamChat(anyList(), any(), eq(List.of())))
            .thenAnswer(inv -> errorIterator("prompt_too_long", 400));

        CompactionService service = buildService(sm, provider);
        List<CompactResult> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(service.compact(CompactTrigger.AUTO, List.of()));
        }
        assertThat(results).allMatch(r -> !r.success());
        CompactResult blocked = service.compact(CompactTrigger.AUTO, List.of());
        assertThat(blocked.success()).isFalse();
        assertThat(blocked.error()).contains("circuit breaker");
    }
}
