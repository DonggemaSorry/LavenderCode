package com.lavendercode.core.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamEventTest {

    @Test
    void shouldCreateContentDelta() {
        var event = new StreamEvent.ContentDelta("Hello World");
        assertThat(event.text()).isEqualTo("Hello World");
        assertThat(event).isInstanceOf(StreamEvent.class);
    }

    @Test
    void shouldCreateThinkingDelta() {
        var event = new StreamEvent.ThinkingDelta("Let me reason about this...");
        assertThat(event.text()).isEqualTo("Let me reason about this...");
        assertThat(event).isInstanceOf(StreamEvent.class);
    }

    @Test
    void shouldCreateStreamComplete() {
        var event = new StreamEvent.StreamComplete();
        assertThat(event).isInstanceOf(StreamEvent.class);
    }

    @Test
    void shouldCreateStreamError() {
        var event = new StreamEvent.StreamError("Service unavailable", 503);
        assertThat(event.message()).isEqualTo("Service unavailable");
        assertThat(event.statusCode()).isEqualTo(503);
        assertThat(event).isInstanceOf(StreamEvent.class);
    }

    @Test
    void shouldCreateStreamErrorWithZeroStatusCode() {
        var event = new StreamEvent.StreamError("Connection refused", 0);
        assertThat(event.message()).isEqualTo("Connection refused");
        assertThat(event.statusCode()).isZero();
    }

    @Test
    void shouldNotBeEqualAcrossDifferentTypes() {
        var delta = new StreamEvent.ContentDelta("text");
        var thinking = new StreamEvent.ThinkingDelta("text");
        var complete = new StreamEvent.StreamComplete();
        var error = new StreamEvent.StreamError("text", 0);

        assertThat(delta).isNotEqualTo(thinking);
        assertThat(delta).isNotEqualTo(error);
        assertThat(delta).isNotEqualTo(complete);
    }

    @Test
    void shouldSupportInstanceOfPatternMatching() {
        StreamEvent event = new StreamEvent.ContentDelta("matched");
        String result;
        if (event instanceof StreamEvent.ContentDelta d) {
            result = "delta:" + d.text();
        } else if (event instanceof StreamEvent.ThinkingDelta t) {
            result = "thinking:" + t.text();
        } else if (event instanceof StreamEvent.StreamComplete) {
            result = "complete";
        } else if (event instanceof StreamEvent.StreamError e) {
            result = "error:" + e.statusCode();
        } else {
            result = "unknown";
        }
        assertThat(result).isEqualTo("delta:matched");
    }
}
