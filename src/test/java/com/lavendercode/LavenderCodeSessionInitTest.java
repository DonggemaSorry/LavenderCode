package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.PersistingSessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.session.SessionTranscriptWriter;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.context.ContextBootstrap;
import com.lavendercode.core.context.SessionHandle;
import com.lavendercode.core.context.SessionIdGenerator;
import com.lavendercode.core.provider.LlmProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LavenderCodeSessionInitTest {
    @Test
    void createsSessionDirectory(@TempDir Path projectRoot) throws Exception {
        ProviderConfig providerConfig = ProviderConfig.of("openai", "openai", "gpt-4", "http://localhost", "key", null);
        LlmConfig config = new LlmConfig(List.of(providerConfig), new Options());
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai");

        SessionHandle handle = ContextBootstrap.create(
            projectRoot, providerConfig, new InMemorySessionManager(), provider, config, null);

        assertThat(handle.contextManager()).isNotNull();
        assertThat(SessionIdGenerator.isNewFormat(handle.sessionId())).isTrue();
        assertThat(Files.exists(projectRoot.resolve(".lavendercode/sessions"))).isTrue();
        assertThat(Files.isDirectory(handle.paths().sessionRoot())).isTrue();
    }

    @Test
    void startupPersistenceWiringCreatesConversationJsonl(@TempDir Path projectRoot) throws Exception {
        ProviderConfig providerConfig = ProviderConfig.of("openai", "openai", "gpt-4", "http://localhost", "key", null);
        LlmConfig config = new LlmConfig(List.of(providerConfig), new Options());
        LlmProvider provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai");
        SessionManager inner = new InMemorySessionManager();
        SessionHandle handle = ContextBootstrap.create(
            projectRoot, providerConfig, inner, provider, config, null);

        try (SessionTranscriptWriter writer = SessionTranscriptWriter.open(handle.paths().conversationJsonl())) {
            SessionManager sessionManager = new PersistingSessionManager(inner, writer, providerConfig.model());

            sessionManager.addUserMessage("hello");
        }

        Path conversationJsonl = handle.paths().conversationJsonl();
        assertThat(Files.exists(conversationJsonl)).isTrue();
        assertThat(Files.readString(conversationJsonl)).contains("\"role\":\"user\"", "hello");
    }
}
