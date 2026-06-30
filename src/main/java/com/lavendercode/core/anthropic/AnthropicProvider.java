package com.lavendercode.core.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import com.lavendercode.core.sse.SseEventReader;
import com.lavendercode.core.sse.SseStreamEventIterator;
import com.lavendercode.core.tool.ToolDefinition;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        return doStreamChat(history, config, null);
    }

    @Override
    public StreamEventIterator streamChat(List<Message> history, LlmConfig config, List<ToolDefinition> toolDefs) {
        return doStreamChat(history, config, toolDefs);
    }

    private StreamEventIterator doStreamChat(List<Message> history, LlmConfig config, List<ToolDefinition> toolDefs) {
        ProviderConfig pc = config.providers().get(0);
        String baseUrl = pc.baseUrl();
        if (baseUrl == null) {
            baseUrl = "https://api.anthropic.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/v1/messages";
        String requestBody = buildRequestBody(history, config, toolDefs);

        Request request = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .header("x-api-key", pc.apiKey())
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

            var body = response.body();
            if (body == null) {
                response.close();
                return new SingleEventIterator(
                    new StreamEvent.StreamError("Empty response body", 0)
                );
            }
            SseEventReader reader = new SseEventReader(body.byteStream());
            return new SseStreamEventIterator(reader, response, call, this::parseSseEvent);

        } catch (IOException e) {
            return new SingleEventIterator(
                new StreamEvent.StreamError("Connection error: " + e.getMessage(), 0)
            );
        }
    }

    String buildRequestBody(List<Message> history, LlmConfig config) {
        return buildRequestBody(history, config, null);
    }

    String buildRequestBody(List<Message> history, LlmConfig config, List<ToolDefinition> toolDefs) {
        ProviderConfig pc = config.providers().get(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", pc.model());
        body.put("max_tokens", config.options().maxTokens());
        body.put("stream", true);

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message msg : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role().name().toLowerCase());

            if (msg.role() == Role.ASSISTANT && !msg.toolCalls().isEmpty()) {
                // Assistant message with tool_use
                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                if (msg.content() != null && !msg.content().isEmpty()) {
                    contentBlocks.add(Map.of("type", "text", "text", msg.content()));
                }
                for (var tc : msg.toolCalls()) {
                    Map<String, Object> toolUse = new LinkedHashMap<>();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.id());
                    toolUse.put("name", tc.name());
                    toolUse.put("input", tc.parameters());
                    contentBlocks.add(toolUse);
                }
                m.put("content", contentBlocks);
            } else if (msg.role() == Role.TOOL && !msg.toolResults().isEmpty()) {
                // Tool result as user message with tool_result blocks
                m.put("role", "user");
                List<Map<String, Object>> contentBlocks = new ArrayList<>();
                for (var tr : msg.toolResults()) {
                    Map<String, Object> toolResult = new LinkedHashMap<>();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", msg.toolCallId());
                    toolResult.put("content", tr.content() != null ? tr.content() : tr.summary());
                    contentBlocks.add(toolResult);
                }
                m.put("content", contentBlocks);
            } else {
                m.put("content", msg.content());
            }
            messages.add(m);
        }
        body.put("messages", messages);

        if (config.options().systemPrompt() != null && !config.options().systemPrompt().isEmpty()) {
            body.put("system", config.options().systemPrompt());
        }

        // Add tools if provided
        if (toolDefs != null && !toolDefs.isEmpty()) {
            List<Map<String, Object>> anthropicTools = new ArrayList<>();
            for (ToolDefinition td : toolDefs) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", td.name());
                t.put("description", td.description());
                t.put("input_schema", td.parameters());
                anthropicTools.add(t);
            }
            body.put("tools", anthropicTools);
        }

        if (pc.thinking() != null && pc.thinking().enabled()) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", pc.thinking().budgetTokens());
            body.put("thinking", thinking);
        }

        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private final Map<Integer, ToolAccum> toolAccumulators = new ConcurrentHashMap<>();

    StreamEvent parseSseEvent(String sseData) {
        try {
            JsonNode node = mapper.readTree(sseData);
            String type = node.get("type").asText();

            return switch (type) {
                case "content_block_start" -> {
                    JsonNode contentBlock = node.get("content_block");
                    if (contentBlock != null && "tool_use".equals(contentBlock.get("type").asText())) {
                        String toolId = contentBlock.get("id").asText();
                        String toolName = contentBlock.get("name").asText();
                        JsonNode indexNode = node.get("index");
                        int index = indexNode != null ? indexNode.asInt() : 0;
                        toolAccumulators.put(index, new ToolAccum(toolId, toolName));
                        yield new StreamEvent.ToolCallStart(toolId, toolName);
                    }
                    yield null;
                }
                case "content_block_delta" -> {
                    JsonNode delta = node.get("delta");
                    String deltaType = delta.get("type").asText();
                    if ("text_delta".equals(deltaType)) {
                        yield new StreamEvent.ContentDelta(delta.get("text").asText());
                    } else if ("thinking_delta".equals(deltaType)) {
                        yield new StreamEvent.ThinkingDelta(delta.get("thinking").asText());
                    } else if ("input_json_delta".equals(deltaType)) {
                        JsonNode idxNode = node.get("index");
                        int index = idxNode != null ? idxNode.asInt() : 0;
                        String partialJson = delta.get("partial_json").asText();
                        ToolAccum tac = toolAccumulators.get(index);
                        if (tac != null) {
                            tac.jsonBuilder.append(partialJson);
                            yield new StreamEvent.ToolCallDelta(tac.toolId, partialJson);
                        }
                        yield null;
                    }
                    yield null;
                }
                case "content_block_stop" -> {
                    JsonNode idxNode = node.get("index");
                    int index = idxNode != null ? idxNode.asInt() : 0;
                    ToolAccum acc = toolAccumulators.remove(index);
                    if (acc != null) {
                        try {
                            Map<String, Object> params = mapper.readValue(
                                acc.jsonBuilder.toString(),
                                new TypeReference<Map<String, Object>>() {}
                            );
                            yield new StreamEvent.ToolCallEnd(acc.toolId, acc.toolName, params);
                        } catch (JsonProcessingException e) {
                            yield new StreamEvent.ToolCallEnd(acc.toolId, acc.toolName, Map.of());
                        }
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

    private static class ToolAccum {
        final String toolId;
        final String toolName;
        final StringBuilder jsonBuilder = new StringBuilder();
        ToolAccum(String toolId, String toolName) {
            this.toolId = toolId;
            this.toolName = toolName;
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
