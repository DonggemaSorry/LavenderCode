package com.lavendercode.core.anthropic;

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

class AnthropicProviderTest {

    private AnthropicProvider provider;
    private MockWebServer mockServer;

    @BeforeEach
    void setUp() {
        provider = new AnthropicProvider();
        mockServer = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void shouldHaveCorrectProtocol() {
        assertThat(provider.protocol()).isEqualTo("anthropic");
    }

    @Test
    void shouldSendCorrectRequestBody() throws Exception {
        String sseBody = "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n" +
                         "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4-20250514", baseUrl, "sk-ant-test", new ThinkingConfig(true, 4000))),
            new Options(4096, "You are helpful.")
        );

        List<Message> history = List.of(
            new Message(Role.USER, "Hello")
        );

        StreamEventIterator iterator = provider.streamChat(history, config);
        while (iterator.hasNext()) {
            iterator.next();
        }
        iterator.close();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("sk-ant-test");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"claude-sonnet-4-20250514\"");
        assertThat(body).contains("\"max_tokens\":4096");
        assertThat(body).contains("\"stream\":true");
        assertThat(body).contains("\"system\":\"You are helpful.\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"content\":\"Hello\"");
        assertThat(body).contains("\"type\":\"enabled\"");
        assertThat(body).contains("\"budget_tokens\":4000");
    }

    @Test
    void shouldYieldStreamEventsFromSSE() throws Exception {
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" World\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\"}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "key", new ThinkingConfig(false, 0))),
            new Options(1024, null)
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
    void shouldYieldThinkingEvent() throws Exception {
        String sseBody =
            "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}\n\n" +
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me think...\"}}\n\n" +
            "data: {\"type\":\"content_block_stop\"}\n\n" +
            "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "key", new ThinkingConfig(true, 1024))),
            new Options(1024, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Complex question"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e1 = iterator.next();
        assertThat(e1).isInstanceOf(StreamEvent.ThinkingDelta.class);
        assertThat(((StreamEvent.ThinkingDelta) e1).text()).isEqualTo("Let me think...");

        iterator.close();
    }

    @Test
    void shouldYieldStreamErrorOnHttpError() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "bad-key", new ThinkingConfig(false, 0))),
            new Options(1024, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        StreamEvent.StreamError error = (StreamEvent.StreamError) e;
        assertThat(error.statusCode()).isEqualTo(401);

        iterator.close();
    }

    @Test
    void shouldYieldStreamErrorOn500() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "key", new ThinkingConfig(false, 0))),
            new Options(1024, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) e).statusCode()).isEqualTo(500);

        iterator.close();
    }

    @Test
    void shouldHandleEmptyMessageHistory() throws Exception {
        String sseBody = "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "key", new ThinkingConfig(false, 0))),
            new Options(1024, null)
        );

        List<Message> history = List.of();

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isInstanceOf(StreamEvent.StreamComplete.class);

        iterator.close();
    }

    @Test
    void shouldNotIncludeSystemFieldWhenEmpty() throws Exception {
        String sseBody = "data: {\"type\":\"message_stop\"}\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4", baseUrl, "key", new ThinkingConfig(false, 0))),
            new Options(1024, "")
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        while (iterator.hasNext()) { iterator.next(); }
        iterator.close();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = request.getBody().readUtf8();
        // Should NOT contain a system field when system_prompt is empty
        assertThat(body).doesNotContain("\"system\"");
    }
}
