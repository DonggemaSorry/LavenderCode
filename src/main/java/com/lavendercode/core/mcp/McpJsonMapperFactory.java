package com.lavendercode.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;

public final class McpJsonMapperFactory {
    private static final McpJsonMapper INSTANCE = new JacksonMcpJsonMapperSupplier().get();

    private McpJsonMapperFactory() {}

    public static McpJsonMapper get() {
        return INSTANCE;
    }

    public static ObjectMapper objectMapper() {
        return ((io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper) INSTANCE).getObjectMapper();
    }
}
