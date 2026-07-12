package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import java.util.ArrayList;
import java.util.List;

public final class RecentTailSelector {
    private final TokenEstimator estimator;

    public RecentTailSelector(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    public List<Message> select(List<Message> history) {
        List<Message> tail = new ArrayList<>();
        int tokens = 0;
        int i = history.size() - 1;

        while (i >= 0) {
            if (wouldLeaveOrphanTool(history, i, tail)) {
                i = backToAssistantToolUse(history, i);
                if (i < 0) break;
            }
            Message m = history.get(i);
            tail.add(0, m);
            tokens += estimateOne(m);
            i--;
            if (tokens >= ContextConstants.RECENT_TAIL_MIN_TOKENS
                && tail.size() >= ContextConstants.RECENT_TAIL_MIN_MESSAGES) {
                break;
            }
        }
        return tail;
    }

    private boolean wouldLeaveOrphanTool(List<Message> history, int i, List<Message> tail) {
        if (history.get(i).role() != Role.TOOL) return false;
        if (tail.isEmpty()) return true;
        return tail.get(0).role() == Role.TOOL;
    }

    private int backToAssistantToolUse(List<Message> history, int i) {
        while (i >= 0 && history.get(i).role() == Role.TOOL) {
            i--;
        }
        if (i >= 0 && history.get(i).role() == Role.ASSISTANT) {
            return i - 1;
        }
        return i;
    }

    private int estimateOne(Message m) {
        return estimator.estimateMessages(List.of(m));
    }
}
