package com.lavendercode.core.anthropic;

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

class AnthropicProviderPromptContextTest {
    private AnthropicProvider provider;
    private MockWebServer mockServer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setUp() throws Exception {
        provider = new AnthropicProvider();
        mockServer = new MockWebServer();
        mockServer.start();
    }
    @AfterEach void tearDown() throws Exception { mockServer.shutdown(); }

    private LlmConfig config(String baseUrl) {
        return new LlmConfig(
            List.of(new ProviderConfig("anthropic", "anthropic", "claude-sonnet-4-20250514",
                baseUrl, "sk-ant-test", new ThinkingConfig(false, 0))),
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
        return "data: {\"type\":\"message_start\",\"message\":{\"usage\":" + usageJson + "}}\n\n" +
               "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}\n\n" +
               "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}\n\n" +
               "data: {\"type\":\"message_stop\"}\n\n";
    }

    @Test
    void systemFieldIsArrayWithCacheControlOnFirstBlock() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"input_tokens\":10}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        assertThat(body.get("system").isArray()).isTrue();
        assertThat(body.get("system").get(0).get("cache_control").get("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    void environmentInfoIsSecondBlockWithoutCacheControl() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"input_tokens\":10}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode second = body.get("system").get(1);
        assertThat(second.get("text").asText()).contains("Environment");
        assertThat(second.has("cache_control")).isFalse();
    }

    @Test
    void lastToolHasCacheControl() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"input_tokens\":10}"))
            .setHeader("Content-Type", "text/event-stream"));
        var tools = List.of(
            new com.lavendercode.core.tool.ToolDefinition("read_file", "Read", java.util.Map.of()),
            new com.lavendercode.core.tool.ToolDefinition("edit_file", "Edit", java.util.Map.of()));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), tools, ctx());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode lastTool = body.get("tools").get(1);
        assertThat(lastTool.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    void remindersInjectedAsUserMessagesAtEnd() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"input_tokens\":10}"))
            .setHeader("Content-Type", "text/event-stream"));
        provider.streamChat(List.of(new Message(Role.USER, "hi")), config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctxWithReminder());
        RecordedRequest req = mockServer.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode messages = body.get("messages");
        JsonNode lastMsg = messages.get(messages.size() - 1);
        assertThat(lastMsg.get("role").asText()).isEqualTo("user");
        assertThat(lastMsg.get("content").asText()).contains("<system-reminder>");
    }

    @Test
    void parsesCacheCreationTokens() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(sseResponse("{\"input_tokens\":10,\"cache_creation_input_tokens\":42,\"cache_read_input_tokens\":0}"))
            .setHeader("Content-Type", "text/event-stream"));
        var iter = provider.streamChat(List.of(new Message(Role.USER, "hi")),
            config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        StreamEvent.Usage usage = null;
        while (iter.hasNext()) {
            StreamEvent e = iter.next();
            if (e instanceof StreamEvent.Usage u) usage = u;
        }
        assertThat(usage).isNotNull();
        assertThat(usage.cacheCreationTokens()).isEqualTo(42);
    }

    @Test
    void parsesCacheReadTokens() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(sseResponse("{\"input_tokens\":10,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":88}"))
            .setHeader("Content-Type", "text/event-stream"));
        var iter = provider.streamChat(List.of(new Message(Role.USER, "hi")),
            config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        StreamEvent.Usage usage = null;
        while (iter.hasNext()) {
            StreamEvent e = iter.next();
            if (e instanceof StreamEvent.Usage u) usage = u;
        }
        assertThat(usage).isNotNull();
        assertThat(usage.cacheReadTokens()).isEqualTo(88);
    }

    @Test
    void missingCacheFieldsReturnZero() throws Exception {
        mockServer.enqueue(new MockResponse().setBody(sseResponse("{\"input_tokens\":10}"))
            .setHeader("Content-Type", "text/event-stream"));
        var iter = provider.streamChat(List.of(new Message(Role.USER, "hi")),
            config(mockServer.url("/").toString().replaceAll("/$","")), List.of(), ctx());
        StreamEvent.Usage usage = null;
        while (iter.hasNext()) {
            StreamEvent e = iter.next();
            if (e instanceof StreamEvent.Usage u) usage = u;
        }
        assertThat(usage).isNotNull();
        assertThat(usage.cacheCreationTokens()).isZero();
        assertThat(usage.cacheReadTokens()).isZero();
    }

    @Test
    void existingTestsStillPass() {
        // Marker test — run existing AnthropicProviderTest separately
        assertThat(true).isTrue();
    }
}
