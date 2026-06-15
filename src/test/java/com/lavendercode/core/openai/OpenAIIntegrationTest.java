package com.lavendercode.core.openai;

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

class OpenAIIntegrationTest {

    private MockWebServer mockServer;
    private OpenAIProvider provider;
    private LlmConfig config;
    private List<Message> history;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        provider = new OpenAIProvider();
        config = new LlmConfig(
            new ProviderConfig("openai", "gpt-4o", baseUrl, "test-key"),
            new Options(1024, null, null)
        );
        history = List.of(new Message(Role.USER, "Hello"));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void shouldHandleFullStreamingResponse() {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
            "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\" there!\"}}]}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        List<StreamEvent> events = new ArrayList<>();
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        iterator.close();

        List<StreamEvent.ContentDelta> deltas = events.stream()
            .filter(e -> e instanceof StreamEvent.ContentDelta)
            .map(e -> (StreamEvent.ContentDelta) e)
            .toList();
        assertThat(deltas).hasSize(2);
        assertThat(deltas.get(0).text()).isEqualTo("Hello");
        assertThat(deltas.get(1).text()).isEqualTo(" there!");
    }

    @Test
    void shouldHandleStreamDisconnect() {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        List<StreamEvent> events = new ArrayList<>();
        while (iterator.hasNext()) {
            events.add(iterator.next());
        }
        iterator.close();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(StreamEvent.ContentDelta.class);
    }
}
