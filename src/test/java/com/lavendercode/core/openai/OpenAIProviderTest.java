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
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "sk-test", null)),
            new Options(2048, "You are helpful.")
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
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "sk-test", null)),
            new Options(2048, null)
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
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "sk-test", null)),
            new Options(2048, null)
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
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "bad-key", null)),
            new Options(2048, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) e).statusCode()).isEqualTo(401);

        iterator.close();
    }

    @Test
    void shouldYieldStreamErrorOn500() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "key", null)),
            new Options(2048, null)
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
    void shouldYieldStreamCompleteForDoneMarker() throws Exception {
        String sseBody = "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "key", null)),
            new Options(2048, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e = iterator.next();
        assertThat(e).isInstanceOf(StreamEvent.StreamComplete.class);

        iterator.close();
    }

    @Test
    void shouldHandleNoSystemPrompt() throws Exception {
        String sseBody = "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "key", null)),
            new Options(2048, "")
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        while (iterator.hasNext()) { iterator.next(); }
        iterator.close();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = request.getBody().readUtf8();
        // Should NOT contain a system message when system_prompt is empty
        assertThat(body).doesNotContain("\"role\":\"system\"");
    }

    @Test
    void shouldRequestUsageInStreamOptions() throws Exception {
        String sseBody = "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "key", null)),
            new Options(2048, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);
        while (iterator.hasNext()) { iterator.next(); }
        iterator.close();

        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"stream_options\":{\"include_usage\":true}");
    }

    @Test
    void shouldEmitUsageFromFinalChunk() throws Exception {
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
            "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: {\"id\":\"3\",\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "gpt-4o", baseUrl, "key", null)),
            new Options(2048, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e1 = iterator.next();
        assertThat(e1).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(((StreamEvent.ContentDelta) e1).text()).isEqualTo("Hi");

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e2 = iterator.next();
        assertThat(e2).isInstanceOf(StreamEvent.Usage.class);
        assertThat(((StreamEvent.Usage) e2).inputTokens()).isEqualTo(10);
        assertThat(((StreamEvent.Usage) e2).outputTokens()).isEqualTo(5);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e3 = iterator.next();
        assertThat(e3).isInstanceOf(StreamEvent.StreamComplete.class);

        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }

    @Test
    void shouldEmitUsageFromDeepSeekFormat() throws Exception {
        // DeepSeek sends usage in the SAME chunk as finish_reason (not a separate empty-choices chunk)
        String sseBody =
            "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
            "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"\",\"role\":null},\"finish_reason\":\"stop\",\"index\":0,\"logprobs\":null}],\"usage\":{\"completion_tokens\":9,\"prompt_tokens\":17,\"total_tokens\":26}}\n\n" +
            "data: [DONE]\n\n";
        mockServer.enqueue(new MockResponse()
            .setBody(sseBody)
            .setHeader("Content-Type", "text/event-stream"));

        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        LlmConfig config = new LlmConfig(
            List.of(ProviderConfig.of("openai", "openai", "deepseek-chat", baseUrl, "key", null)),
            new Options(2048, null)
        );

        List<Message> history = List.of(new Message(Role.USER, "Hi"));

        StreamEventIterator iterator = provider.streamChat(history, config);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e1 = iterator.next();
        assertThat(e1).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(((StreamEvent.ContentDelta) e1).text()).isEqualTo("Hi");

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e2 = iterator.next();
        assertThat(e2).isInstanceOf(StreamEvent.Usage.class);
        assertThat(((StreamEvent.Usage) e2).inputTokens()).isEqualTo(17);
        assertThat(((StreamEvent.Usage) e2).outputTokens()).isEqualTo(9);

        assertThat(iterator.hasNext()).isTrue();
        StreamEvent e3 = iterator.next();
        assertThat(e3).isInstanceOf(StreamEvent.StreamComplete.class);

        assertThat(iterator.hasNext()).isFalse();
        iterator.close();
    }
}
