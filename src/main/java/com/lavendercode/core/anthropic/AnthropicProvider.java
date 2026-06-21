package com.lavendercode.core.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.sse.SseEventReader;
import com.lavendercode.core.sse.SseStreamEventIterator;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AnthropicProvider implements LlmProvider {

    private static final String PROTOCOL = "anthropic";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    public AnthropicProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    @Override
    public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
        String baseUrl = config.provider().baseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/v1/messages";
        String requestBody = buildRequestBody(history, config);

        Request request = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .header("x-api-key", config.provider().apiKey())
            .header("anthropic-version", "2023-06-01")
            .build();

        Call call = httpClient.newCall(request);
        try {
            Response response = call.execute();
            if (!response.isSuccessful()) {
                int code = response.code();
                String message = response.message();
                response.close();
                return new SingleEventIterator(
                    new StreamEvent.StreamError(
                        "HTTP " + code + ": " + message,
                        code
                    )
                );
            }

            SseEventReader reader = new SseEventReader(response.body().byteStream());
            return new SseStreamEventIterator(reader, response, call, this::parseSseEvent);

        } catch (IOException e) {
            return new SingleEventIterator(
                new StreamEvent.StreamError("Connection error: " + e.getMessage(), 0)
            );
        }
    }

    String buildRequestBody(List<Message> history, LlmConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.provider().model());
        body.put("max_tokens", config.options().maxTokens());
        body.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message msg : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role().name().toLowerCase());
            m.put("content", msg.content());
            messages.add(m);
        }
        body.put("messages", messages);

        if (config.options().systemPrompt() != null && !config.options().systemPrompt().isEmpty()) {
            body.put("system", config.options().systemPrompt());
        }

        if (config.options().thinking() != null && config.options().thinking().enabled()) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", config.options().thinking().budgetTokens());
            body.put("thinking", thinking);
        }

        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    StreamEvent parseSseEvent(String sseData) {
        try {
            JsonNode node = mapper.readTree(sseData);
            String type = node.get("type").asText();

            return switch (type) {
                case "content_block_delta" -> {
                    JsonNode delta = node.get("delta");
                    String deltaType = delta.get("type").asText();
                    if ("text_delta".equals(deltaType)) {
                        yield new StreamEvent.ContentDelta(delta.get("text").asText());
                    } else if ("thinking_delta".equals(deltaType)) {
                        yield new StreamEvent.ThinkingDelta(delta.get("thinking").asText());
                    }
                    yield null;
                }
                case "message_stop" -> new StreamEvent.StreamComplete();
                default -> null;
            };
        } catch (JsonProcessingException e) {
            return new StreamEvent.StreamError("Failed to parse SSE event: " + e.getMessage(), 0);
        }
    }

    private static class SingleEventIterator implements StreamEventIterator {
        private boolean consumed = false;
        private final StreamEvent event;

        SingleEventIterator(StreamEvent event) {
            this.event = event;
        }

        @Override
        public boolean hasNext() {
            return !consumed;
        }

        @Override
        public StreamEvent next() {
            consumed = true;
            return event;
        }

        @Override
        public void close() {}
    }
}
