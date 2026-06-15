package com.lavendercode.chat.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.lavendercode.core.provider.*;
import com.lavendercode.chat.session.SessionManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TuiApplication {

    private final LlmProvider provider;
    private final SessionManager sessionManager;
    private final String modelName;
    private final Screen screen;
    private final WindowBasedTextGUI textGUI;
    private final Label chatArea;
    private final Label statusBar;
    private final TextBox inputBox;
    private final AtomicBoolean isProcessing;
    private final AtomicBoolean running;

    public TuiApplication(LlmProvider provider, SessionManager sessionManager,
                          String modelName, Screen screen) throws IOException {
        this.provider = provider;
        this.sessionManager = sessionManager;
        this.modelName = modelName;
        this.screen = screen;
        this.isProcessing = new AtomicBoolean(false);
        this.running = new AtomicBoolean(true);

        this.textGUI = new MultiWindowTextGUI(screen);

        BasicWindow window = new BasicWindow("LavenderCode");
        window.setHints(java.util.List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        // Status bar
        this.statusBar = new Label("");
        updateStatus();
        mainPanel.addComponent(statusBar.withBorder(Borders.singleLine("Status")));

        // Chat area
        Panel chatPanel = new Panel();
        this.chatArea = new Label("Welcome to LavenderCode!\n\n" +
            "Type your message and press Enter to chat.\n" +
            "Commands: /exit, /clear, /help\n");
        chatPanel.addComponent(chatArea);
        mainPanel.addComponent(chatPanel.withBorder(Borders.singleLine("Chat")));

        // Input area
        Panel inputPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        inputPanel.addComponent(new Label("> "));
        this.inputBox = new TextBox("", TextBox.Style.SINGLE_LINE);
        this.inputBox.setPreferredSize(new TerminalSize(60, 1));
        inputPanel.addComponent(inputBox);
        mainPanel.addComponent(inputPanel.withBorder(Borders.singleLine("Input")));

        window.setComponent(mainPanel);

        // Handle input submission
        inputBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                handleInput(inputBox.getText());
                inputBox.setText("");
                return false;
            }
            if (keyStroke.getKeyType() == KeyType.Escape) {
                handleExit();
                return false;
            }
            return true;
        });

        textGUI.addWindow(window);
    }

    public void run() throws IOException {
        screen.startScreen();
        try {
            while (running.get()) {
                textGUI.updateScreen();
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            screen.stopScreen();
        }
    }

    private void handleInput(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (trimmed.startsWith("/")) {
            handleCommand(trimmed);
            return;
        }

        if (isProcessing.get()) {
            appendToChat("[System] Please wait for the current response to complete.\n");
            return;
        }

        isProcessing.set(true);
        appendToChat("You: " + trimmed + "\n");
        sessionManager.addUserMessage(trimmed);

        new Thread(() -> {
            try {
                appendToChat("AI: ");
                com.lavendercode.core.config.LlmConfig config =
                    com.lavendercode.core.config.ConfigLoader.load(
                        java.nio.file.Path.of("config.yaml")
                    );

                StreamEventIterator iterator = provider.streamChat(
                    sessionManager.getHistory(),
                    config
                );

                StringBuilder fullResponse = new StringBuilder();
                while (iterator.hasNext()) {
                    StreamEvent event = iterator.next();
                    if (event instanceof StreamEvent.ContentDelta delta) {
                        appendToChat(delta.text());
                        fullResponse.append(delta.text());
                    } else if (event instanceof StreamEvent.ThinkingDelta thinking) {
                        appendToChat("[思考] " + thinking.text() + "\n");
                    } else if (event instanceof StreamEvent.StreamError error) {
                        appendToChat("\n[Error] " + error.message() + "\n");
                    }
                }
                iterator.close();

                if (!fullResponse.isEmpty()) {
                    appendToChat("\n");
                    sessionManager.addAssistantMessage(fullResponse.toString());
                }
            } catch (Exception e) {
                appendToChat("\n[Error] " + e.getMessage() + "\n");
            } finally {
                isProcessing.set(false);
                updateStatus();
            }
        }).start();
    }

    private void appendToChat(String text) {
        chatArea.setText(chatArea.getText() + text);
    }

    private void updateStatus() {
        statusBar.setText("Model: " + modelName +
            " | Messages: " + sessionManager.getMessageCount() +
            " | Protocol: " + provider.protocol());
    }

    private void handleCommand(String command) {
        String cmd = command.toLowerCase();
        if ("/exit".equals(cmd) || "/quit".equals(cmd)) {
            handleExit();
        } else if ("/clear".equals(cmd)) {
            sessionManager.clear();
            chatArea.setText("Conversation cleared.\n\n");
            updateStatus();
        } else if ("/help".equals(cmd)) {
            appendToChat(
                "Commands:\n" +
                "  /exit  - Exit LavenderCode\n" +
                "  /clear - Clear conversation history\n" +
                "  /help  - Show this help\n\n"
            );
        } else {
            appendToChat("Unknown command: " + command + " (use /help)\n");
        }
    }

    private void handleExit() {
        running.set(false);
        if (textGUI.getActiveWindow() != null) {
            textGUI.getActiveWindow().close();
        }
        try {
            screen.stopScreen();
        } catch (IOException e) {
            // ignore
        }
    }
}
