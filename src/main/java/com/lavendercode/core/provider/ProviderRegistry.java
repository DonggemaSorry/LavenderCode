package com.lavendercode.core.provider;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderRegistry {
    private static final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();

    static {
        ServiceLoader.load(LlmProvider.class).forEach(p -> providers.put(p.protocol(), p));
    }

    public static void register(LlmProvider provider) {
        providers.put(provider.protocol(), provider);
    }

    public static LlmProvider get(String protocol) {
        LlmProvider provider = providers.get(protocol);
        if (provider == null) {
            throw new IllegalArgumentException(
                "No provider registered for protocol: " + protocol
            );
        }
        return provider;
    }
}
