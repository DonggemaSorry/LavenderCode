package com.lavendercode.chat.terminal;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads keystrokes in raw mode and renders the draft through {@link TerminalRenderer}.
 */
public class InputSystem {

    private static final long RENDER_TIMEOUT_MS = 500;

    private final Terminal terminal;
    private final TerminalKeyReader keyReader;
    private final BlockingQueue<InputEvent> inputQueue;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public InputSystem(Terminal terminal,
                       BlockingQueue<InputEvent> inputQueue,
                       BlockingQueue<RenderEvent> renderQueue,
                       InputAreaLayout inputLayout) {
        this.terminal = terminal;
        this.keyReader = new TerminalKeyReader(terminal);
        this.inputQueue = inputQueue;
        this.renderQueue = renderQueue;
    }

    public void run() {
        Attributes saved = null;
        try {
            saved = terminal.enterRawMode();
            keyReader.enableBracketedPaste();
            while (!shutdown.get()) {
                String line = readEditedLine();
                if (line == null) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("/")) {
                    inputQueue.offer(parseCommand(line.trim()));
                } else {
                    inputQueue.offer(new InputEvent.SendMessage(line));
                }
            }
        } catch (IOException e) {
            if (!shutdown.get()) {
                inputQueue.offer(new InputEvent.Shutdown());
            }
        } finally {
            keyReader.disableBracketedPaste();
            publishDraftSync("", 0);
            if (saved != null) {
                terminal.setAttributes(saved);
            }
        }
    }

    private String readEditedLine() throws IOException {
        StringBuilder buffer = new StringBuilder();
        int cursor = 0;
        publishDraftSync(buffer.toString(), cursor);

        while (true) {
            TerminalInput input = keyReader.readInput();

            switch (input) {
                case TerminalInput.Scroll(var cmd) -> {
                    inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.SCROLL, cmd));
                }
                case TerminalInput.Submit() -> {
                    publishDraftSync("", 0);
                    return buffer.toString();
                }
                case TerminalInput.Newline() -> {
                    buffer.insert(cursor, '\n');
                    cursor++;
                    publishDraftSync(buffer.toString(), cursor);
                }
                case TerminalInput.Paste(var text) -> {
                    String normalized = normalizeNewlines(text);
                    if (!normalized.isEmpty()) {
                        buffer.insert(cursor, normalized);
                        cursor += normalized.length();
                        publishDraftSync(buffer.toString(), cursor);
                    }
                }
                case TerminalInput.Character(var code) -> {
                    if (code == 4) {
                        if (buffer.isEmpty()) {
                            publishDraftSync("", 0);
                            inputQueue.offer(new InputEvent.Shutdown());
                            return null;
                        }
                        continue;
                    }
                    if (code == 3) {
                        publishDraftSync("", 0);
                        inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, ""));
                        return "";
                    }
                    if (code == 127 || code == 8) {
                        if (cursor > 0) {
                            buffer.deleteCharAt(cursor - 1);
                            cursor--;
                            publishDraftSync(buffer.toString(), cursor);
                        }
                        continue;
                    }
                    if (code == 27) {
                        continue;
                    }
                    if (code >= 32) {
                        buffer.insert(cursor, (char) code);
                        cursor++;
                        publishDraftSync(buffer.toString(), cursor);
                    }
                }
            }
        }
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void publishDraftSync(String draft, int cursor) {
        var latch = new CountDownLatch(1);
        try {
            renderQueue.put(new RenderEvent.UpdateInputDraft(draft, cursor, latch));
            latch.await(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void requestShutdown() {
        shutdown.set(true);
        try {
            terminal.close();
        } catch (IOException e) {
            // Terminal closing during shutdown
        }
    }

    private InputEvent parseCommand(String input) {
        String line = input.toLowerCase();
        if (line.equals("/exit") || line.equals("/quit")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, "");
        }
        if (line.equals("/clear")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, "");
        }
        if (line.equals("/help")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
        }
        if (line.startsWith("/theme ")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.THEME, input.substring(7).trim());
        }
        if (line.startsWith("/scroll")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.SCROLL, input.substring(8).trim());
        }
        if (line.equals("/cancel")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, "");
        }
        return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
    }
}
