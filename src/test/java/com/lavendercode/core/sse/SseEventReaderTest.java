package com.lavendercode.core.sse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventReaderTest {

    @Test
    void shouldReadEventsIncrementally() {
        String sse =
            "data: {\"n\":1}\n\n" +
            "data: {\"n\":2}\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        try (SseEventReader reader = new SseEventReader(stream)) {
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.next()).isEqualTo("{\"n\":1}");
            assertThat(reader.hasNext()).isTrue();
            assertThat(reader.next()).isEqualTo("{\"n\":2}");
            assertThat(reader.hasNext()).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parseStreamShouldMatchIncrementalReads() {
        String sse =
            "data: hello\n\n" +
            "data: world\n\n";
        InputStream stream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        List<String> incremental = new ArrayList<>();
        try (SseEventReader reader = new SseEventReader(stream)) {
            while (reader.hasNext()) {
                incremental.add(reader.next());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(SseEventParser.parseStream(
            new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8))
        )).containsExactlyElementsOf(incremental);
    }
}
