package com.lavendercode.core.hook;

import java.util.Map;

public sealed interface HookAction {
    record Shell(String command) implements HookAction {}
    record Prompt(String text) implements HookAction {}
    record Http(String url, String method, Map<String, String> headers, String body) implements HookAction {}
    record Subagent(String agentName, String prompt) implements HookAction {}
}
