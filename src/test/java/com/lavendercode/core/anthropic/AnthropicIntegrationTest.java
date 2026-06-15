package com.lavendercode.core.anthropic;

import com.lavendercode.core.config.*;
import com.lavendercode.core.provider.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicIntegrationTest {

    private MockWebServer mockServer;
    private AnthropicProvider provider;
    private LlmConfig config;
    private List<Message> history;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        provider = new AnthropicProvider();
        config = new LlmConfig(
            new ProviderConfig("anthropic", "claude-sonnet-4", baseUrl, "test-key"),
            new Options(1024, "You are helpful.", new ThinkingConfig(false, 0))
        );
        history = List.of(new Message(Role.USER, "What is 2+2?"));
    }

    @AfterEach
    void tearDown() {
        try {
            mockServer.shutdown();
        } catch (Exception ignored) {
            // Server may already be shut down (e.g. in shouldHandleConnectionRefused)
        }
    }

    @Test
    void shouldHandleFullStreamingResponse() {
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"2+2\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" equals\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" 4.\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\"}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        List<StreamEvent> events = new ArrayList<>();
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        iterator.close();

        List<StreamEvent> deltas = events.stream()
            .filter(e -> e instanceof StreamEvent.ContentDelta)
            .toList();
        assertThat(deltas).hasSize(3);
        assertThat(((StreamEvent.ContentDelta) deltas.get(0)).text()).isEqualTo("2+2");
        assertThat(((StreamEvent.ContentDelta) deltas.get(1)).text()).isEqualTo(" equals");
        assertThat(((StreamEvent.ContentDelta) deltas.get(2)).text()).isEqualTo(" 4.");

        assertThat(events.get(events.size() - 1))
            .isInstanceOf(StreamEvent.StreamComplete.class);
    }

    @Test
    void shouldHandleThinkingStreamingResponse() {
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me calculate...\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\"}\n\n" +
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"4\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\"}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        List<StreamEvent> events = new ArrayList<>();
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        iterator.close();

        List<StreamEvent.ThinkingDelta> thinkings = events.stream()
            .filter(e -> e instanceof StreamEvent.ThinkingDelta)
            .map(e -> (StreamEvent.ThinkingDelta) e)
            .toList();
        assertThat(thinkings).hasSize(1);
        assertThat(thinkings.get(0).text()).isEqualTo("Let me calculate...");
    }

    @Test
    void shouldHandleConnectionRefused() throws Exception {
        // Shut down server to simulate connection refused
        mockServer.shutdown();

        StreamEventIterator iterator = provider.streamChat(history, config);
        assertThat(iterator.hasNext()).isTrue();
        StreamEvent event = iterator.next();
        assertThat(event).isInstanceOf(StreamEvent.StreamError.class);
        iterator.close();
    }
}
