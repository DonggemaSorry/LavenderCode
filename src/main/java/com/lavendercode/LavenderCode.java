package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.TerminalChatApplication;
import com.lavendercode.chat.terminal.Theme;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;

public class LavenderCode {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of("config.yaml");

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            }
        }

        LlmConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
            if (!java.nio.file.Files.exists(configPath)) {
                System.err.println("Run: copy config.yaml.example config.yaml");
                System.err.println("Then edit config.yaml with your API key.");
            }
            System.exit(1);
            return;
        }

        System.out.println("Loaded config for protocol: " + config.provider().protocol());
        System.out.println("Model: " + config.provider().model());

        LlmProvider provider = ProviderRegistry.get(config.provider().protocol());
        SessionManager sessionManager = new InMemorySessionManager();

        Terminal terminal = TerminalBuilder.builder()
            .name("LavenderCode")
            .system(true)
            .build();

        TerminalChatApplication app = new TerminalChatApplication(
            sessionManager, provider,
            config.provider().model(), config,
            Theme.dark()
        );
        app.run(terminal);
    }
}
