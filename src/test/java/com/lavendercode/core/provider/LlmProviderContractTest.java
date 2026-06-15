package com.lavendercode.core.provider;

import com.lavendercode.core.config.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class LlmProviderContractTest {

    abstract LlmProvider provider();
    abstract String validModel();
    abstract String sseResponse();

    MockWebServer mockServer;
    LlmConfig config;
    List<Message> history;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        config = new LlmConfig(
            new ProviderConfig(provider().protocol(), validModel(), baseUrl, "test-key"),
            new Options(1024, "You are helpful.", null)
        );
        history = List.of(new Message(Role.USER, "Hello"));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void mustReturnCorrectProtocol() {
        assertThat(provider().protocol()).isNotEmpty();
    }

    @Test
    void mustYieldStreamCompleteAfterFullResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(sseResponse())
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider().streamChat(history, config);

        StreamEvent lastEvent = null;
        while (iterator.hasNext()) {
            lastEvent = iterator.next();
        }
        iterator.close();

        assertThat(lastEvent).isNotNull();
        assertThat(lastEvent).isInstanceOf(StreamEvent.StreamComplete.class);
    }

    @Test
    void mustYieldStreamErrorOn401() {
        mockServer.enqueue(new MockResponse().setResponseCode(401));

        StreamEventIterator iterator = provider().streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) e).statusCode()).isEqualTo(401);
        iterator.close();
    }

    @Test
    void mustYieldStreamErrorOn429() {
        mockServer.enqueue(new MockResponse()
            .setResponseCode(429)
            .addHeader("Retry-After", "30"));

        StreamEventIterator iterator = provider().streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) e).statusCode()).isEqualTo(429);
        iterator.close();
    }

    @Test
    void mustSetAuthorizationHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(sseResponse())
            .setHeader("Content-Type", "text/event-stream"));

        StreamEventIterator iterator = provider().streamChat(history, config);
        while (iterator.hasNext()) {
            iterator.next();
        }
        iterator.close();

        var request = mockServer.takeRequest();
        String auth = request.getHeader("Authorization");
        String apiKey = request.getHeader("x-api-key");
        assertThat(auth != null || apiKey != null)
            .as("Auth header must be set")
            .isTrue();
    }
}
