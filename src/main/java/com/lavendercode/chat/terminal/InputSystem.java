package com.lavendercode.chat.terminal;

import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input system using JLine3 LineReader.
 * Enter = submit, Alt+Enter = newline, Ctrl+C = cancel, Ctrl+D = shutdown.
 */
public class InputSystem {

    private final LineReader reader;
    private final BlockingQueue<InputEvent> inputQueue;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public InputSystem(Terminal terminal, BlockingQueue<InputEvent> inputQueue) {
        this.inputQueue = inputQueue;

        Path historyPath = Path.of(System.getProperty("user.home"), ".lavendercode_history");

        LineReaderBuilder builder = LineReaderBuilder.builder()
            .terminal(terminal)
            .variable(LineReader.HISTORY_FILE, historyPath)
            .variable(LineReader.HISTORY_SIZE, 1000)
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, "  ")
            .variable(LineReader.INDENTATION, 2)
            .completer(new StringsCompleter(
                "/exit", "/quit", "/clear", "/help",
                "/theme dark", "/theme light", "/scroll top",
                "/scroll bottom", "/scroll up", "/scroll down"));

        this.reader = builder.build();

        // Alt+Enter -> insert newline
        KeyMap<Binding> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
        keyMap.bind(new Macro(LineReader.SELF_INSERT), KeyMap.alt(KeyMap.ctrl('J')));
    }

    /**
     * Blocking loop that reads user input and publishes InputEvents.
     * Runs on InputThread until shutdown.
     */
    public void run() {
        while (!shutdown.get()) {
            String line;
            try {
                // Position prompt
                terminal().puts(InfoCmp.Capability.cursor_address,
                    terminal().getHeight() - 2, 0);
                terminal().flush();
                line = reader.readLine("> ");
            } catch (UserInterruptException e) {
                // Ctrl+C -> cancel
                inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, ""));
                continue;
            } catch (EndOfFileException e) {
                inputQueue.offer(new InputEvent.Shutdown());
                break;
            }

            if (line == null) {
                inputQueue.offer(new InputEvent.Shutdown());
                break;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("/")) {
                inputQueue.offer(parseCommand(trimmed));
            } else {
                inputQueue.offer(new InputEvent.SendMessage(line));
            }
        }
    }

    public void requestShutdown() {
        shutdown.set(true);
        try {
            terminal().close();
        } catch (IOException e) {
            // Terminal closing during shutdown
        }
    }

    private org.jline.terminal.Terminal terminal() {
        return reader.getTerminal();
    }

    private InputEvent parseCommand(String input) {
        String line = input.toLowerCase();
        if (line.equals("/exit") || line.equals("/quit"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, "");
        if (line.equals("/clear"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, "");
        if (line.equals("/help"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
        if (line.startsWith("/theme "))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.THEME,
                input.substring(7).trim());
        if (line.startsWith("/scroll"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.SCROLL,
                input.substring(8).trim());
        if (line.equals("/cancel"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, "");
        return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
    }
}
