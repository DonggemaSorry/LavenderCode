package com.lavendercode.core.hook;

import java.nio.file.Path;
import java.util.*;

public record HookPayload(HookEvent event, Map<String, Object> fields) {

    public Map<String, Object> toMap() {
        var m = new TreeMap<String, Object>();
        m.put("event", event.name());
        m.putAll(fields);
        return m;
    }

    public static Builder builder(HookEvent event) { return new Builder(event); }

    public static final class Builder {
        private final HookEvent event;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        Builder(HookEvent event) { this.event = event; }

        public Builder sessionId(String id) { fields.put("session_id", id); return this; }
        public Builder cwd(Path p) { fields.put("cwd", p.toString()); return this; }
        public Builder mode(String m) { fields.put("mode", m); return this; }
        public Builder put(String key, Object value) { fields.put(key, value); return this; }

        public HookPayload build() {
            return new HookPayload(event, Map.copyOf(fields));
        }
    }
}
