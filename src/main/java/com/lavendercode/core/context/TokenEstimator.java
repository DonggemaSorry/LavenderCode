package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import java.util.List;

public final class TokenEstimator {
    private volatile UsageAnchor anchor = new UsageAnchor(0);
    private volatile int anchoredCharCount = 0;

    public void replaceAnchor(int input, int cacheRead, int cacheCreation, int output) {
        this.anchor = new UsageAnchor(input + cacheRead + cacheCreation + output);
    }

    public void resetAnchor() {
        this.anchor = new UsageAnchor(0);
        this.anchoredCharCount = 0;
    }

    public int getAnchorTotal() {
        return anchor.totalTokens();
    }

    public void setAnchoredCharCount(int count) {
        this.anchoredCharCount = count;
    }

    public int estimateMessages(List<Message> history) {
        int deltaChars = Math.max(0, totalChars(history) - anchoredCharCount);
        return anchor.totalTokens()
            + (int) Math.ceil(deltaChars / ContextConstants.ESTIMATE_CHARS_PER_TOKEN);
    }

    public void syncCharCount(List<Message> history) {
        this.anchoredCharCount = totalChars(history);
    }

    private static int totalChars(List<Message> history) {
        int sum = 0;
        for (Message m : history) {
            if (m.content() != null) sum += m.content().length();
            if (m.toolResults() != null) {
                for (var tr : m.toolResults()) {
                    if (tr.content() != null) sum += tr.content().length();
                    else if (tr.summary() != null) sum += tr.summary().length();
                }
            }
        }
        return sum;
    }
}
