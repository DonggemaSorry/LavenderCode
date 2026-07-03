package com.lavendercode.core.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.config.*;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.provider.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAIProviderPromptContextTest {
    private OpenAIProvider provider;
    private MockWebServer mockServer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setUp() throws Exception {
        provider = new OpenAIProvider();
        mockServer = new MockWebServer();
        mockServer.start();
    }
    @AfterEach void tearDown() throws Exception { mockServer.shutdown(); }

    private LlmConfig config(String baseUrl) {
        return new LlmConfig(
            List.of(new ProviderConfig("openai", "openai", "gpt-4o", baseUrl, "sk-test", null)),
            new Options(4096, ""));
    }

    private PromptContext ctx() {
        return new PromptContext("You are stable.", "## Environment\n- OS: test", List.of());
    }

    private PromptContext ctxWithReminder() {
        return new PromptContext("You are stable.", "## Environment", List.of(
            "<system-reminder>Plan mode: read-only only.</system-reminder>"));
    }

    private String sseResponse(String usageJson) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n" +
               "data: {\"choices\":[],\"usage\":" + usageJson + "}\n\n" +
               "data: [DONE]\n\n";
    }

    @Test
    void stablePromptIsFirstSystemMessage() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"prompt_tokens\":10,\"completion_tokens\":5}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(0).get("content").asText()).isEqualTo("You are stable.");
    }

    @Test
    void environmentInfoIsSecondSystemMessage() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"prompt_tokens\":10,\"completion_tokens\":5}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        assertThat(body.get("messages").get(1).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asText()).contains("Environment");
    }

    @Test
    void remindersInjectedAsUserMessagesAtEnd() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"prompt_tokens\":10,\"completion_tokens\":5}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctxWithReminder());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode messages = body.get("messages");
        JsonNode last = messages.get(messages.size() - 1);
        assertThat(last.get("role").asText()).isEqualTo("user");
        assertThat(last.get("content").asText()).contains("<system-reminder>");
    }

    @Test
    void parsesCachedTokens() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse(
            "{\"prompt_tokens\":10,\"completion_tokens\":5,\"prompt_tokens_details\":{\"cached_tokens\":77}}"))
            .setHeader("Content-Type", "text/event-stream"));
        var iter = provider.streamChat(List.of(new Message(Role.USER, "hi")),
            config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        StreamEvent.Usage usage = null;
        while (iter.hasNext()) {
            StreamEvent e = iter.next();
            if (e instanceof StreamEvent.Usage u) usage = u;
        }
        assertThat(usage).isNotNull();
        assertThat(usage.cacheReadTokens()).isEqualTo(77);
        assertThat(usage.cacheCreationTokens()).isZero();
    }

    @Test
    void missingCachedTokensReturnZero() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse(
            "{\"prompt_tokens\":10,\"completion_tokens\":5}"))
            .setHeader("Content-Type", "text/event-stream"));
        var iter = provider.streamChat(List.of(new Message(Role.USER, "hi")),
            config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        StreamEvent.Usage usage = null;
        while (iter.hasNext()) {
            StreamEvent e = iter.next();
            if (e instanceof StreamEvent.Usage u) usage = u;
        }
        assertThat(usage).isNotNull();
        assertThat(usage.cacheReadTokens()).isZero();
    }

    @Test
    void noCacheControlMarkers() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"prompt_tokens\":10,\"completion_tokens\":5}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        RecordedRequest req = mockServer.takeRequest();
        String bodyStr = req.getBody().readUtf8();
        assertThat(bodyStr).doesNotContain("cache_control");
    }
}
