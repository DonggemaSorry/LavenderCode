package com.lavendercode.core.openai;

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
            baseUrl = "https://api.openai.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/v1/chat/completions";
        String requestBody = buildRequestBody(history, config, toolDefs);

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
        return buildRequestBody(history, config, null);
    }

    String buildRequestBody(List<Message> history, LlmConfig config, List<ToolDefinition> toolDefs) {
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

            if (msg.role() == Role.ASSISTANT && !msg.toolCalls().isEmpty()) {
                // Assistant message with tool_calls
                if (msg.content() != null && !msg.content().isEmpty()) {
                    m.put("content", msg.content());
                }
                List<Map<String, Object>> toolCallsList = new ArrayList<>();
                for (var tc : msg.toolCalls()) {
                    Map<String, Object> tcObj = new LinkedHashMap<>();
                    tcObj.put("id", tc.id());
                    tcObj.put("type", "function");
                    Map<String, Object> func = new LinkedHashMap<>();
                    func.put("name", tc.name());
                    func.put("arguments", toJsonString(tc.parameters()));
                    tcObj.put("function", func);
                    toolCallsList.add(tcObj);
                }
                m.put("tool_calls", toolCallsList);
            } else if (msg.role() == Role.TOOL && !msg.toolResults().isEmpty()) {
                // Tool message
                m.put("role", "tool");
                m.put("tool_call_id", msg.toolCallId());
                var tr = msg.toolResults().get(0);
                m.put("content", tr.content() != null ? tr.content() : tr.summary());
            } else {
                m.put("content", msg.content());
            }
            messages.add(m);
        }
        body.put("messages", messages);

        if (config.options().maxTokens() > 0) {
            body.put("max_tokens", config.options().maxTokens());
        }

        // Add tools if provided
        if (toolDefs != null && !toolDefs.isEmpty()) {
            List<Map<String, Object>> oaiTools = new ArrayList<>();
            for (ToolDefinition td : toolDefs) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("type", "function");
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", td.name());
                f.put("description", td.description());
                f.put("parameters", td.parameters());
                t.put("function", f);
                oaiTools.add(t);
            }
            body.put("tools", oaiTools);
        }

        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private String toJsonString(Map<String, Object> params) {
        try {
            return mapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private final Map<Integer, ToolAccum> toolAccumulators = new LinkedHashMap<>();

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

            // Check for finish_reason == "tool_calls" -> emit ToolCallEnd for all accumulators
            JsonNode finishReason = choices.get(0).get("finish_reason");
            if (finishReason != null && "tool_calls".equals(finishReason.asText())) {
                // Emit all pending tool call ends
                for (var entry : toolAccumulators.entrySet()) {
                    ToolAccum acc = entry.getValue();
                    try {
                        Map<String, Object> params = mapper.readValue(
                            acc.jsonBuilder.toString(),
                            new TypeReference<Map<String, Object>>() {}
                        );
                        return new StreamEvent.ToolCallEnd(acc.toolId, acc.toolName, params);
                    } catch (JsonProcessingException e) {
                        return new StreamEvent.ToolCallEnd(acc.toolId, acc.toolName, Map.of());
                    }
                }
                return new StreamEvent.StreamComplete();
            }

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) {
                return null;
            }

            // Handle tool_calls in delta
            JsonNode toolCalls = delta.get("tool_calls");
            if (toolCalls != null && !toolCalls.isNull()) {
                for (JsonNode tc : toolCalls) {
                    int index = tc.get("index").asInt();
                    JsonNode func = tc.get("function");
                    if (func == null) continue;

                    JsonNode nameNode = func.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        // First appearance with name -> ToolCallStart
                        String toolName = nameNode.asText();
                        String toolId = "call_" + index;
                        toolAccumulators.put(index, new ToolAccum(toolId, toolName));
                        return new StreamEvent.ToolCallStart(toolId, toolName);
                    }

                    JsonNode argsNode = func.get("arguments");
                    if (argsNode != null && !argsNode.isNull()) {
                        String args = argsNode.asText();
                        ToolAccum acc = toolAccumulators.get(index);
                        if (acc != null) {
                            acc.jsonBuilder.append(args);
                            return new StreamEvent.ToolCallDelta(acc.toolId, args);
                        }
                    }
                }
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
