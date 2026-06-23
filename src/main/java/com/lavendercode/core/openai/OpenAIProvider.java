package com.lavendercode.core.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.sse.SseEventReader;
import com.lavendercode.core.sse.SseStreamEventIterator;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OpenAIProvider implements LlmProvider {

    private static final String PROTOCOL = "openai";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient;

    public OpenAIProvider() {
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
        ProviderConfig pc = config.providers().get(0);
        String baseUrl = pc.baseUrl();
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/v1/chat/completions";
        String requestBody = buildRequestBody(history, config);

        Request request = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .header("Authorization", "Bearer " + pc.apiKey())
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
        ProviderConfig pc = config.providers().get(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", pc.model());
        body.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (config.options().systemPrompt() != null && !config.options().systemPrompt().isEmpty()) {
            Map<String, Object> systemMsg = new LinkedHashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", config.options().systemPrompt());
            messages.add(systemMsg);
        }

        for (Message msg : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role().name().toLowerCase());
            m.put("content", msg.content());
            messages.add(m);
        }
        body.put("messages", messages);

        if (config.options().maxTokens() > 0) {
            body.put("max_tokens", config.options().maxTokens());
        }

        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    StreamEvent parseSseEvent(String sseData) {
        if ("[DONE]".equals(sseData.trim())) {
            return new StreamEvent.StreamComplete();
        }

        try {
            JsonNode node = mapper.readTree(sseData);
            JsonNode choices = node.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) {
                return null;
            }

            JsonNode content = delta.get("content");
            if (content == null || content.isNull()) {
                return null;
            }

            return new StreamEvent.ContentDelta(content.asText());
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
