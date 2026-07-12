package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {
    @Test
    void replacesAnchorNotAccumulates() {
        TokenEstimator est = new TokenEstimator();
        est.replaceAnchor(1000, 0, 0, 200);
        est.replaceAnchor(500, 100, 0, 50);
        assertThat(est.getAnchorTotal()).isEqualTo(650);
    }

    @Test
    void estimatesDeltaByCharsPerToken() {
        TokenEstimator est = new TokenEstimator();
        est.replaceAnchor(1000, 0, 0, 0);
        est.setAnchoredCharCount(0);
        List<Message> history = List.of(new Message(Role.USER, "1234567890"));
        int estimate = est.estimateMessages(history);
        assertThat(estimate).isEqualTo(1000 + (int) Math.ceil(10 / ContextConstants.ESTIMATE_CHARS_PER_TOKEN));
    }
}
