package com.lavendercode.core.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.lavendercode.chat.terminal.LeadMailQueue;
import org.junit.jupiter.api.Test;

class ReminderInjectorLeadMailTest {
    @Test
    void drainsLeadMailQueue() {
        LeadMailQueue q = new LeadMailQueue();
        q.offer("<team-update>hello</team-update>");
        var r = ReminderInjector.inject(1, false, null, null, q);
        assertThat(r).isPresent();
        assertThat(r.get()).contains("<team-update>hello</team-update>");
        assertThat(q.drain()).isEmpty();
    }
}
