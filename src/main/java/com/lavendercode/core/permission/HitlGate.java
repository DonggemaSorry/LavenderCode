package com.lavendercode.core.permission;

import java.util.concurrent.atomic.AtomicBoolean;

public interface HitlGate {
    HitlChoice awaitDecision(HitlRequest request, AtomicBoolean cancelFlag);
}
