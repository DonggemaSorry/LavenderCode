package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionCatalog;
import com.lavendercode.chat.session.SessionListItem;
import com.lavendercode.core.permission.HitlChoice;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
    private final HitlCoordinator hitlCoordinator;
    private final NetworkOrchestrator orchestrator;
    private final Path projectSessionsDir;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public InputSystem(Terminal terminal,
                       BlockingQueue<InputEvent> inputQueue,
                       BlockingQueue<RenderEvent> renderQueue,
                       InputAreaLayout inputLayout,
                       HitlCoordinator hitlCoordinator) {
        this(terminal, inputQueue, renderQueue, inputLayout, hitlCoordinator, null, null);
    }

    public InputSystem(Terminal terminal,
                       BlockingQueue<InputEvent> inputQueue,
                       BlockingQueue<RenderEvent> renderQueue,
                       InputAreaLayout inputLayout,
                       HitlCoordinator hitlCoordinator,
                       NetworkOrchestrator orchestrator,
                       Path projectSessionsDir) {
        this.terminal = terminal;
        this.keyReader = new TerminalKeyReader(terminal);
        this.inputQueue = inputQueue;
        this.renderQueue = renderQueue;
        this.hitlCoordinator = hitlCoordinator;
        this.orchestrator = orchestrator;
        this.projectSessionsDir = projectSessionsDir;
    }

    public InputSystem(Terminal terminal,
                       BlockingQueue<InputEvent> inputQueue,
                       BlockingQueue<RenderEvent> renderQueue,
                       InputAreaLayout inputLayout) {
        this(terminal, inputQueue, renderQueue, inputLayout, null);
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
                    InputEvent event = parseCommand(line.trim());
                    if (event != null) {
                        inputQueue.offer(event);
                    }
                    if (shouldStopAfter(event)) {
                        shutdown.set(true);
                        break;
                    }
                } else {
                    inputQueue.offer(new InputEvent.SendMessage(line));
                }
            }
        } catch (IOException e) {
            if (!shutdown.get()) {
                inputQueue.offer(new InputEvent.Shutdown());
            }
        } finally {
            if (!shutdown.get()) {
                keyReader.disableBracketedPaste();
                publishDraftSync("", 0);
                if (saved != null) {
                    terminal.setAttributes(saved);
                }
            }
        }
    }

    static boolean shouldStopAfter(InputEvent event) {
        return event instanceof InputEvent.ExecuteCommand cmd
            && (cmd.type() == InputEvent.CommandType.EXIT || cmd.type() == InputEvent.CommandType.QUIT);
    }

    private String readEditedLine() throws IOException {
        StringBuilder buffer = new StringBuilder();
        int cursor = 0;
        publishDraftSync(buffer.toString(), cursor);

        while (true) {
            TerminalInput input = keyReader.readInput();

            if (hitlCoordinator != null && hitlCoordinator.isAwaiting()) {
                if (handleHitlInput(input)) {
                    continue;
                }
            }

            switch (input) {
                case TerminalInput.Special(var key) when key == TerminalInput.SpecialKey.SHIFT_TAB -> {
                    inputQueue.offer(new InputEvent.CyclePermissionMode());
                }
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
                case TerminalInput.Exit() -> {
                    publishDraftSync("", 0);
                    shutdown.set(true);
                    inputQueue.offer(new InputEvent.Shutdown());
                    return null;
                }
                case TerminalInput.Escape() -> {
                    if (hitlCoordinator != null && hitlCoordinator.isAwaiting()) {
                        inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.DENY));
                        inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.ESC_CANCEL, ""));
                    } else {
                        inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.ESC_CANCEL, ""));
                    }
                }
                case TerminalInput.Character(var code) -> {
                    if (code == 4) {
                        if (buffer.isEmpty()) {
                            publishDraftSync("", 0);
                            shutdown.set(true);
                            inputQueue.offer(new InputEvent.Shutdown());
                            return null;
                        }
                        continue;
                    }
                    if (code == 3) {
                        if (hitlCoordinator != null && hitlCoordinator.isAwaiting()) {
                            inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.DENY));
                            inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.ESC_CANCEL, ""));
                            continue;
                        }
                        publishDraftSync("", 0);
                        shutdown.set(true);
                        inputQueue.offer(new InputEvent.Shutdown());
                        return null;
                    }
                    if (code == 127 || code == 8) {
                        if (cursor > 0) {
                            buffer.deleteCharAt(cursor - 1);
                            cursor--;
                            publishDraftSync(buffer.toString(), cursor);
                        }
                        continue;
                    }
                    if (code >= 32) {
                        buffer.insert(cursor, (char) code);
                        cursor++;
                        publishDraftSync(buffer.toString(), cursor);
                    }
                }
                default -> { /* ignore other specials during normal editing */ }
            }
        }
    }

    private boolean handleHitlInput(TerminalInput input) {
        switch (input) {
            case TerminalInput.Character(var code) -> {
                if (code == '1') {
                    inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.ALLOW_ONCE));
                    return true;
                }
                if (code == '2') {
                    inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.ALLOW_PERMANENT));
                    return true;
                }
                if (code == '3') {
                    inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.DENY));
                    return true;
                }
                if (code == 3) {
                    inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.DENY));
                    inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.ESC_CANCEL, ""));
                    return true;
                }
            }
            case TerminalInput.Escape() -> {
                inputQueue.offer(new InputEvent.HitlChoice(HitlChoice.DENY));
                inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.ESC_CANCEL, ""));
                return true;
            }
            case TerminalInput.Submit() -> {
                inputQueue.offer(new InputEvent.HitlChoice(choiceForIndex(hitlCoordinator.selectedIndex())));
                return true;
            }
            case TerminalInput.Scroll(var cmd) -> {
                if ("up".equals(cmd)) {
                    hitlCoordinator.navigateSelection(-1);
                } else if ("down".equals(cmd)) {
                    hitlCoordinator.navigateSelection(1);
                }
                return true;
            }
            default -> { }
        }
        return false;
    }

    private static HitlChoice choiceForIndex(int index) {
        return switch (index) {
            case 1 -> HitlChoice.ALLOW_PERMANENT;
            case 2 -> HitlChoice.DENY;
            default -> HitlChoice.ALLOW_ONCE;
        };
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void publishDraftSync(String draft, int cursor) {
        if (shutdown.get()) {
            return;
        }
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
        if (input.toLowerCase().startsWith("/scroll")) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.SCROLL, input.substring(8).trim());
        }
        BuiltinCommandRegistry.ParseResult r = BuiltinCommandRegistry.parse(input);
        if (r.type() == InputEvent.CommandType.UNKNOWN) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.UNKNOWN, r.hint());
        }
        if (r.type() == InputEvent.CommandType.RESUME) {
            return handleResumeCommand();
        }
        return new InputEvent.ExecuteCommand(r.type(), r.args());
    }

    private InputEvent handleResumeCommand() {
        if (orchestrator == null || projectSessionsDir == null) {
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.RESUME, "");
        }

        String blocked = ResumeGate.check(orchestrator.isAgentRunning(), orchestrator.isResuming());
        if (blocked != null) {
            publishSystemMessage("[" + blocked + "]");
            return null;
        }

        try {
            List<SessionListItem> items = SessionCatalog.list(projectSessionsDir);
            if (items.isEmpty()) {
                publishSystemMessage("[没有可恢复的会话]");
                return null;
            }
            SessionListItem selected = SessionPicker.pick(terminal, items);
            if (selected == null) {
                publishSystemMessage("[已取消恢复会话]");
                return null;
            }
            return new InputEvent.ResumeSession(selected.sessionId());
        } catch (IOException e) {
            publishSystemMessage("[读取会话列表失败: " + e.getMessage() + "]");
            return null;
        }
    }

    private void publishSystemMessage(String text) {
        try {
            renderQueue.put(new RenderEvent.AddSystemMessage(text));
            renderQueue.put(new RenderEvent.FinalizeMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
