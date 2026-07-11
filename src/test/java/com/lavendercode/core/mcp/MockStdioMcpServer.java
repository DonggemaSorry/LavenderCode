package com.lavendercode.core.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Minimal stdio MCP server for tests. Uses newline-delimited JSON-RPC (SDK 1.0 stdio transport).
 */
public final class MockStdioMcpServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockStdioMcpServer() {}

    public static void main(String[] args) throws Exception {
        boolean readOnly = args.length > 0 && "readonly".equals(args[0]);
        run(readOnly);
    }

    static void run(boolean readOnly) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                BufferedWriter out =
                        new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode request = MAPPER.readTree(line);
                JsonNode response = respond(request, readOnly);
                if (response != null) {
                    out.write(MAPPER.writeValueAsString(response));
                    out.newLine();
                    out.flush();
                }
            }
        }
    }

    static JsonNode respond(JsonNode request, boolean readOnly) {
        if (!request.has("id") || request.get("id").isNull()) {
            return null;
        }
        String method = request.path("method").asText();
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request.get("id"));
        ObjectNode result = MAPPER.createObjectNode();
        switch (method) {
            case "initialize" -> {
                ObjectNode serverInfo = MAPPER.createObjectNode();
                serverInfo.put("name", "mock");
                serverInfo.put("version", "1.0");
                result.set("serverInfo", serverInfo);
                result.put("protocolVersion", "2024-11-05");
                ObjectNode capabilities = MAPPER.createObjectNode();
                capabilities.set("tools", MAPPER.createObjectNode());
                result.set("capabilities", capabilities);
            }
            case "tools/list" -> {
                ArrayNode tools = MAPPER.createArrayNode();
                ObjectNode tool = MAPPER.createObjectNode();
                tool.put("name", "echo");
                tool.put("description", "Echo tool");
                ObjectNode inputSchema = MAPPER.createObjectNode();
                inputSchema.put("type", "object");
                ObjectNode properties = MAPPER.createObjectNode();
                ObjectNode message = MAPPER.createObjectNode();
                message.put("type", "string");
                properties.set("message", message);
                inputSchema.set("properties", properties);
                tool.set("inputSchema", inputSchema);
                if (readOnly) {
                    ObjectNode annotations = MAPPER.createObjectNode();
                    annotations.put("readOnlyHint", true);
                    tool.set("annotations", annotations);
                }
                tools.add(tool);
                result.set("tools", tools);
            }
            case "tools/call" -> {
                String message = request.path("params").path("arguments").path("message").asText("ok");
                ArrayNode content = MAPPER.createArrayNode();
                ObjectNode text = MAPPER.createObjectNode();
                text.put("type", "text");
                text.put("text", "echo:" + message);
                content.add(text);
                result.set("content", content);
                result.put("isError", false);
            }
            default -> {
                ObjectNode error = MAPPER.createObjectNode();
                error.put("code", -32601);
                error.put("message", "Method not found: " + method);
                response.set("error", error);
                return response;
            }
        }
        response.set("result", result);
        return response;
    }
}
