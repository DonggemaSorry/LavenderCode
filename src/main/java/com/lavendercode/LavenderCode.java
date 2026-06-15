package com.lavendercode;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorColorConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorDeviceConfiguration;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.tui.TuiApplication;

import java.io.IOException;
import java.nio.file.Path;

public class LavenderCode {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of("config.yaml");
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            }
        }

        LlmConfig config = ConfigLoader.load(configPath);
        System.out.println("Loaded config for protocol: " + config.provider().protocol());
        System.out.println("Model: " + config.provider().model());

        LlmProvider provider = ProviderRegistry.get(config.provider().protocol());

        SessionManager sessionManager = new InMemorySessionManager();

        Screen screen = createScreen();

        TuiApplication app = new TuiApplication(
            provider,
            sessionManager,
            config.provider().model(),
            config,
            screen
        );

        app.run();
    }

    private static Screen createScreen() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            System.out.println("Windows detected, using Swing terminal emulator...");
            return createSwingScreen();
        }

        return new DefaultTerminalFactory().createScreen();
    }

    private static Screen createSwingScreen() throws IOException {
        TerminalEmulatorDeviceConfiguration deviceConfig = new TerminalEmulatorDeviceConfiguration(
            100, 30,
            TerminalEmulatorDeviceConfiguration.CursorStyle.UNDER_BAR,
            TextColor.ANSI.WHITE,
            true
        );
        SwingTerminalFrame frame = new SwingTerminalFrame(
            "LavenderCode",
            deviceConfig,
            SwingTerminalFontConfiguration.getDefault(),
            TerminalEmulatorColorConfiguration.getDefault()
        );
        return new TerminalScreen(frame);
    }
}
