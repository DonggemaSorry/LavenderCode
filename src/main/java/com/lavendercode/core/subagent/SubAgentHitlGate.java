package com.lavendercode.core.subagent;

import com.lavendercode.core.permission.HitlChoice;
import com.lavendercode.core.permission.HitlGate;
import com.lavendercode.core.permission.HitlRequest;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SubAgentHitlGate implements HitlGate {

    private final HitlGate delegate;
    private final String agentName;

    public SubAgentHitlGate(HitlGate delegate, String agentName) {
        this.delegate = delegate;
        this.agentName = agentName;
    }

    @Override
    public HitlChoice awaitDecision(HitlRequest request, AtomicBoolean cancelFlag) {
        String prefixedDetail = "[来自 SubAgent " + agentName + "] " + request.detail();
        HitlRequest wrapped = new HitlRequest(
            request.toolName(), prefixedDetail, request.reason(), request.selectedIndex());
        return delegate.awaitDecision(wrapped, cancelFlag);
    }
}
