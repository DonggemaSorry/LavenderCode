package com.lavendercode.core.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

public final class McpTransportFactory {
    private McpTransportFactory() {}

    public static McpClientTransport createTransport(McpServerConfig config, McpJsonMapper jsonMapper) {
        return switch (config.type()) {
            case STDIO -> createStdio(config, jsonMapper);
            case HTTP -> createHttp(config, jsonMapper);
        };
    }

    public static McpSyncClient createClient(McpClientTransport transport) {
        return McpClient.sync(transport)
            .requestTimeout(McpConstants.CALL_TIMEOUT)
            .capabilities(McpSchema.ClientCapabilities.builder().build())
            .build();
    }

    private static McpClientTransport createStdio(McpServerConfig config, McpJsonMapper jsonMapper) {
        ServerParameters.Builder builder = ServerParameters.builder(config.command());
        if (!config.args().isEmpty()) {
            builder.args(config.args());
        }
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putAll(config.env());
        builder.env(env);
        StdioClientTransport transport = new StdioClientTransport(builder.build(), jsonMapper);
        transport.setStdErrorHandler(line -> System.err.print(line));
        return transport;
    }

    private static McpClientTransport createHttp(McpServerConfig config, McpJsonMapper jsonMapper) {
        HttpClientStreamableHttpTransport.Builder builder =
            HttpClientStreamableHttpTransport.builder(config.url()).jsonMapper(jsonMapper);
        if (!config.headers().isEmpty()) {
            builder.customizeRequest(req -> applyHeaders(req, config.headers()));
        }
        return builder.build();
    }

    private static void applyHeaders(HttpRequest.Builder req, Map<String, String> headers) {
        headers.forEach(req::header);
    }
}
