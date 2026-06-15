package com.lavendercode.core.sse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventParserTest {

    @Test
    void shouldParseSingleDataEvent() {
        String sse = "data: {\"type\":\"content\",\"text\":\"hello\"}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isEqualTo("{\"type\":\"content\",\"text\":\"hello\"}");
    }

    @Test
    void shouldParseMultipleDataEvents() {
        String sse =
            "data: {\"type\":\"content\",\"text\":\"hello\"}\n\n" +
            "data: {\"type\":\"content\",\"text\":\" world\"}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo("{\"type\":\"content\",\"text\":\"hello\"}");
        assertThat(events.get(1)).isEqualTo("{\"type\":\"content\",\"text\":\" world\"}");
    }

    @Test
    void shouldHandleMultiLineData() {
        String sse =
            "data: line1\n" +
            "data: line2\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isEqualTo("line1\nline2");
    }

    @Test
    void shouldSkipEmptyData() {
        String sse = "data: \n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).isEmpty();
    }

    @Test
    void shouldIgnoreCommentLines() {
        String sse =
            ": this is a comment\n" +
            "data: {\"text\":\"hello\"}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isEqualTo("{\"text\":\"hello\"}");
    }

    @Test
    void shouldIgnoreEventField() {
        String sse =
            "event: message\n" +
            "data: {\"text\":\"hello\"}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isEqualTo("{\"text\":\"hello\"}");
    }

    @Test
    void shouldHandleDedupedBlankLine() {
        String sse =
            "data: {\"text\":\"hello\"}\n\n\n" +
            "data: {\"text\":\"world\"}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo("{\"text\":\"hello\"}");
        assertThat(events.get(1)).isEqualTo("{\"text\":\"world\"}");
    }

    @Test
    void shouldReturnEmptyListForEmptyStream() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);

        List<String> events = SseEventParser.parseStream(stream);

        assertThat(events).isEmpty();
    }
}
