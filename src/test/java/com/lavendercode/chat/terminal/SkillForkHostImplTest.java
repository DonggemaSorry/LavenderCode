package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.subagent.AgentCatalog;
import com.lavendercode.core.subagent.SubAgentServices;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class SkillForkHostImplTest {

    @Test
    void runSubAgentUsesLauncher() {
        AtomicBoolean called = new AtomicBoolean(false);
        var provider = new com.lavendercode.core.provider.LlmProvider() {
            @Override
            public String protocol() { return "test"; }

            @Override
            public StreamEventIterator streamChat(
                    List<Message> history, LlmConfig config) {
                called.set(true);
                return new StreamEventIterator() {
                    private boolean done;
                    public boolean hasNext() { return !done; }
                    public StreamEvent next() {
                        done = true;
                        return new StreamEvent.ContentDelta("real result");
                    }
                    public void close() {}
                };
            }
        };
        var catalog = new AgentCatalog();
        var services = new SubAgentServices(
            catalog, provider,
            new LlmConfig(List.of(ProviderConfig.of("t", "openai", "m", "http://x", "k", null)),
                new Options()),
            java.nio.file.Path.of("."),
            new Options(),
            null,
            (req, f) -> com.lavendercode.core.permission.HitlChoice.ALLOW_ONCE,
            null, null, null, () -> List.of());

        var orch = new NetworkOrchestrator(
            new DeltaBuffer(
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
                new java.util.concurrent.LinkedBlockingQueue<>()),
            new java.util.concurrent.LinkedBlockingQueue<>(),
            new java.util.concurrent.LinkedBlockingQueue<>(),
            new com.lavendercode.chat.session.InMemorySessionManager(),
            provider, "test", "m",
            new LlmConfig(List.of(ProviderConfig.of("t", "openai", "m", "http://x", "k", null)),
                new Options()),
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(),
            java.nio.file.Path.of("."));

        var host = new SkillForkHostImpl(orch, services);
        String result = host.runSubAgent("do work", List.of(new Message(Role.USER, "ctx")),
            List.of("read_file"), "inherit");
        assertThat(called).isTrue();
        assertThat(result).isEqualTo("real result");
        assertThat(result).doesNotContain("[Skill fork 结果]");
    }
}
