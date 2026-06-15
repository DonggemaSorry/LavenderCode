package com.lavendercode.core.openai;

import com.lavendercode.core.config.*;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIProviderTest {

    private OpenAIProvider provider;
    private MockWebServer mockServer;

    @BeforeEach
    void setUp() {
        provider = new OpenAIProvider();
        mockServer = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void shouldHaveCorrectProtocol() {
        assertThat(provider.protocol()).isEqualTo("openai");
    }

    @Test
    void shouldSendCorrectRequestBody() throws Exception {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai", "gpt-4o", baseUrl, "sk-test"),
            new Options(2048, "You are helpful.", null)
        );

        List<Message> history = List.of(
            new Message(Role.SYSTEM, "You are helpful."),
            new Message(Role.USER, "Hello")
        );

        StreamEventIterator iterator = provider.streamChat(history, config);
        while (iterator.hasNext()) {
            iterator.next();
        }
        iterator.close();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"gpt-4o\"");
        assertThat(body).contains("\"stream\":true");
        assertThat(body).contains("\"role\":\"system\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"content\":\"Hello\"");
    }

    @Test
    void shouldYieldStreamEventsFromSSE() throws Exception {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
            "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\" World\"}}]}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai", "gpt-4o", baseUrl, "sk-test"),
            new Options(2048, null, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e1 = iterator.next();
        assertThat(e1).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(((StreamEvent.ContentDelta) e1).text()).isEqualTo("Hello");

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e2 = iterator.next();
        assertThat(e2).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(((StreamEvent.ContentDelta) e2).text()).isEqualTo(" World");

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e3 = iterator.next();
        assertThat(e3).isInstanceOf(StreamEvent.StreamComplete.class);

        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }

    @Test
    void shouldHandleEmptyDelta() throws Exception {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{}}]}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai", "gpt-4o", baseUrl, "sk-test"),
            new Options(2048, null, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isInstanceOf(StreamEvent.StreamComplete.class);
        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }

    @Test
    void shouldYieldStreamErrorOn401() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("openai", "gpt-4o", baseUrl, "bad-key"),
            new Options(2048, null, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) e).statusCode()).isEqualTo(401);

        iterator.close();
    }
}
