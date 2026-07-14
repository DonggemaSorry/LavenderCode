package com.lavendercode.core.hook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HookEngineImpl implements HookEngine {

    private final HookConfig config;
    private final HookReminderQueue reminderQueue = new HookReminderQueue();
    private final Set<String> onceSet = new HashSet<>();

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public HookEngineImpl(HookConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override public HookConfig config() { return config; }
    @Override public HookReminderQueue reminderQueue() { return reminderQueue; }
    @Override public void clearOnce() { onceSet.clear(); }

    // ── dispatch ────────────────────────────────────────────────────────────

    @Override
    public HookInterceptResult dispatch(HookEvent event, HookPayload payload, AtomicBoolean cancelFlag) {
        for (HookRule rule : config.rules()) {
            if (rule.event() != event) continue;
            if (!ConditionMatcher.matches(rule.condition(), payload)) continue;
            if (rule.onlyOnce() && onceSet.contains(rule.name())) continue;

            onceSet.add(rule.name());

            if (rule.async()) {
                Thread.startVirtualThread(() -> executeAction(rule, payload, event));
                continue;
            }

            var result = executeAction(rule, payload, event);
            if (event.interceptable() && result != null && result.blocked()) {
                return result;
            }
        }
        return HookInterceptResult.allowed();
    }

    // ── action dispatch ─────────────────────────────────────────────────────

    private HookInterceptResult executeAction(HookRule rule, HookPayload payload, HookEvent event) {
        try {
            return switch (rule.action()) {
                case HookAction.Shell s -> executeShell(rule, s, payload, event);
                case HookAction.Prompt p -> {
                    reminderQueue.add(p.text());
                    yield null;
                }
                case HookAction.Http h -> executeHttp(rule, h, payload, event);
                case HookAction.Subagent sa -> {
                    System.err.println("[hook subagent] not yet implemented, skipped: " + rule.name());
                    yield null;
                }
            };
        } catch (Exception e) {
            System.err.println("[hook " + rule.name() + "] " + event + " failed: " + e.getMessage());
            return null;
        }
    }

    // ── shell action ────────────────────────────────────────────────────────

    private HookInterceptResult executeShell(HookRule rule, HookAction.Shell shell,
                                             HookPayload payload, HookEvent event) throws Exception {
        ProcessBuilder pb;
        if (IS_WINDOWS) {
            pb = new ProcessBuilder("cmd", "/c", shell.command());
        } else {
            pb = new ProcessBuilder("sh", "-c", shell.command());
        }
        pb.redirectErrorStream(false);

        Process proc = pb.start();
        try (var os = proc.getOutputStream()) {
            os.write(JSON.writeValueAsBytes(payload.toMap()));
        }

        boolean done = proc.waitFor(rule.timeout().toSeconds(), TimeUnit.SECONDS);
        if (!done) {
            proc.destroyForcibly();
            System.err.println("[hook " + rule.name() + "] " + event + " failed: timeout " + rule.timeout());
            return null;
        }

        int exitCode = proc.exitValue();
        if (exitCode == 0) return null;

        if (exitCode == 2 && event.interceptable()) {
            String stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
            String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
            String reason = stderr.isEmpty() ? stdout : stderr;
            return HookInterceptResult.blocked(rule.name(), reason);
        }

        System.err.println("[hook " + rule.name() + "] " + event + " failed: exit code " + exitCode);
        return null;
    }

    // ── HTTP action ─────────────────────────────────────────────────────────

    private HookInterceptResult executeHttp(HookRule rule, HookAction.Http http,
                                            HookPayload payload, HookEvent event) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(rule.timeout()).build();

        String body = http.body() != null
                ? renderTemplate(http.body(), payload)
                : JSON.writeValueAsString(payload.toMap());

        var builder = HttpRequest.newBuilder(URI.create(http.url()))
                .timeout(rule.timeout())
                .header("Content-Type", "application/json");

        if ("GET".equalsIgnoreCase(http.method())) {
            builder.GET();
        } else {
            builder.method(http.method(), HttpRequest.BodyPublishers.ofString(body));
        }

        if (http.headers() != null) {
            http.headers().forEach(builder::header);
        }

        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (event.interceptable() && response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                @SuppressWarnings("unchecked")
                var json = JSON.readValue(response.body(), Map.class);
                if ("block".equals(json.get("decision"))) {
                    String reason = json.getOrDefault("reason", "blocked").toString();
                    return HookInterceptResult.blocked(rule.name(), reason);
                }
            } catch (Exception ignored) {
                // non-JSON response — not a block decision
            }
        }
        return null;
    }

    // ── template rendering ──────────────────────────────────────────────────

    private String renderTemplate(String template, HookPayload payload) {
        String result = template;
        for (var entry : flattenMap("", payload.toMap()).entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue().toString());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenMap(String prefix, Map<String, Object> map) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                result.putAll(flattenMap(key, (Map<String, Object>) nested));
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}
