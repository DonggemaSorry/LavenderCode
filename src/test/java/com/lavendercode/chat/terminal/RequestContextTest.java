package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    @Test
    void shouldStartAsNotCancelled() {
        RequestContext ctx = new RequestContext(null, null);
        assertThat(ctx.isCancelled()).isFalse();
    }

    @Test
    void cancelShouldSetCancelled() {
        RequestContext ctx = new RequestContext(null, null);
        ctx.cancel();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void cancelShouldBeIdempotent() {
        RequestContext ctx = new RequestContext(null, null);
        ctx.cancel();
        ctx.cancel();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void shouldAcceptNullCallAndIterator() {
        RequestContext ctx = new RequestContext(null, null);
        assertThat(ctx).isNotNull();
    }
}
