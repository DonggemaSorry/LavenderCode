package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.ProviderSelector;
import com.lavendercode.chat.terminal.TerminalChatApplication;
import com.lavendercode.chat.terminal.Theme;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.Options;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import com.lavendercode.core.tool.*;
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

        Terminal terminal = TerminalBuilder.builder()
            .name("LavenderCode")
            .system(true)
            .build();

        ProviderConfig selectedProvider;
        if (config.providers().size() == 1) {
            selectedProvider = config.providers().get(0);
        } else {
            selectedProvider = ProviderSelector.select(terminal, config.providers());
        }

        String providerName = selectedProvider.name() != null
            ? selectedProvider.name()
            : selectedProvider.protocol() + "-" + selectedProvider.model();

        // Register tools with configured limits
        Options opts = config.options();
        if (opts.toolSystemEnabled()) {
            ToolRegistry.register(new ReadFileTool(opts.readFileMaxLines()));
            ToolRegistry.register(new WriteFileTool());
            ToolRegistry.register(new EditFileTool());
            ToolRegistry.register(new ExecuteCommandTool(opts.commandExecutionEnabled(),
                opts.commandTimeoutSeconds(), opts.commandOutputMaxChars()));
            ToolRegistry.register(new GlobTool(opts.searchMaxResults()));
            ToolRegistry.register(new GrepTool(opts.searchMaxResults()));
        }

        LlmProvider provider = ProviderRegistry.get(selectedProvider.protocol());
        SessionManager sessionManager = new InMemorySessionManager();

        TerminalChatApplication app = new TerminalChatApplication(
            sessionManager, provider,
            providerName, selectedProvider.model(), config,
            Theme.dark()
        );
        app.run(terminal);
    }
}
