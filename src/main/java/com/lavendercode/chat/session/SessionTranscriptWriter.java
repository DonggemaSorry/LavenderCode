package com.lavendercode.chat.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolCall;
import com.lavendercode.core.tool.ToolResult;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public final class SessionTranscriptWriter implements Closeable {
    private static final Logger logger = Logger.getLogger(SessionTranscriptWriter.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();
    private final BufferedWriter writer;
    private final FileChannel channel;

    private SessionTranscriptWriter(BufferedWriter writer, FileChannel channel) {
        this.writer = writer;
        this.channel = channel;
    }

    public static SessionTranscriptWriter open(Path jsonl) throws IOException {
        Path parent = jsonl.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FileOutputStream out = new FileOutputStream(jsonl.toFile(), true);
        return new SessionTranscriptWriter(
            new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)),
            out.getChannel()
        );
    }

    public void appendMessage(Role role, String content,
                              List<ToolCall> toolCalls, List<ToolResult> toolResults,
                              String modelOrNull) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", roleJson(role));
        if (content != null) {
            node.put("content", content);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            node.set("tool_calls", mapper.valueToTree(toolCalls));
        }
        if (toolResults != null && !toolResults.isEmpty()) {
            node.set("tool_results", mapper.valueToTree(toolResults));
        }
        node.put("ts", Instant.now().getEpochSecond());
        if (modelOrNull != null) {
            node.put("model", modelOrNull);
        }
        writeLine(node);
    }

    public void appendMessage(Message message, String modelOrNull) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", roleJson(message.role()));
        if (message.content() != null) {
            node.put("content", message.content());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            node.set("tool_calls", mapper.valueToTree(message.toolCalls()));
        }
        if (message.toolResults() != null && !message.toolResults().isEmpty()) {
            node.set("tool_results", mapper.valueToTree(message.toolResults()));
        }
        if (message.role() == Role.TOOL && message.toolCallId() != null) {
            node.put("tool_call_id", message.toolCallId());
        }
        node.put("ts", Instant.now().getEpochSecond());
        if (modelOrNull != null) {
            node.put("model", modelOrNull);
        }
        writeLine(node);
    }

    public void appendCompactMarker() {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "compact");
        node.put("ts", Instant.now().getEpochSecond());
        writeLine(node);
    }

    private void writeLine(ObjectNode node) {
        lock.lock();
        try {
            writer.write(mapper.writeValueAsString(node));
            writer.newLine();
            writer.flush();
            channel.force(true);
        } catch (IOException e) {
            logger.warning("JSONL append failed: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private static String roleJson(Role role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case SYSTEM -> "system";
        };
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
