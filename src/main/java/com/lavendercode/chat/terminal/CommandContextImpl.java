package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.PersistingSessionManager;
import com.lavendercode.core.command.CommandContext;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class CommandContextImpl implements CommandContext {
    private final NetworkOrchestrator orch;
    private final Terminal terminal;
    private final Path projectSessionsDir;

    CommandContextImpl(NetworkOrchestrator orch, Terminal terminal, Path projectSessionsDir) {
        this.orch = orch;
        this.terminal = terminal;
        this.projectSessionsDir = projectSessionsDir;
    }

    @Override public String currentModeLabel() { return orch.modeManager.getMode().label(); }
    @Override public int totalInputTokens() { return orch.tokenAccumulator.getTotalInput(); }
    @Override public int totalOutputTokens() { return orch.tokenAccumulator.getTotalOutput(); }
    @Override public int toolCount() { return orch.modeManager.getToolDefinitions(orch.options.toolSystemEnabled()).size(); }
    @Override public int memoryEntryCount() { return orch.memoryService != null ? orch.memoryService.fileNames().size() : 0; }
    @Override public String modelName() { return orch.modelName; }
    @Override public Path workingDirectory() { return orch.projectRoot; }
    @Override public List<String> memoryFileNames() { return orch.memoryService != null ? orch.memoryService.fileNames() : List.of(); }
    @Override public String sessionId() { return orch.handle != null ? orch.handle.sessionId() : "(in-memory)"; }
    @Override public Path sessionArchivePath() { return orch.handle != null ? orch.handle.paths().conversationJsonl() : null; }
    @Override public void printMessage(String text) { orch.safePut(new RenderEvent.AddSystemMessage(text)); }

    @Override
    public void enterPlanMode() {
        orch.modeManager.enterPlanMode();
        int tokens = orch.tokenAccumulator.getTotalInput() + orch.tokenAccumulator.getTotalOutput();
        orch.safePut(new RenderEvent.StatusUpdate(orch.modeManager.getMode().label(), orch.modelName, "", tokens));
        orch.safePut(new RenderEvent.AddSystemMessage("[已进入计划模式 · 仅只读工具可用]"));
    }

    @Override
    public void exitPlanToDefault() {
        orch.modeManager.exitPlanToDefault();
        int tokens = orch.tokenAccumulator.getTotalInput() + orch.tokenAccumulator.getTotalOutput();
        orch.safePut(new RenderEvent.StatusUpdate(orch.modeManager.getMode().label(), orch.modelName, "", tokens));
        orch.safePut(new RenderEvent.AddSystemMessage("[已退出计划模式 · 所有工具可用]"));
    }

    @Override
    public void clearAndNewSession() {
        orch.deltaBuffer.forceFlush();
        if (orch.sessionManager instanceof PersistingSessionManager persisting && orch.projectRoot != null) {
            persisting.suspendPersistence();
            persisting.clear();
            try { persisting.startNewSession(orch.projectRoot); }
            catch (IOException e) { orch.safePut(new RenderEvent.AddSystemMessage("[创建新会话存档失败: " + e.getMessage() + "]")); }
            persisting.resumePersistence();
        } else {
            orch.sessionManager.clear();
        }
        orch.tokenAccumulator.reset();
        orch.turnCount = 0;
        orch.safePut(new RenderEvent.ClearChat());
        orch.safePut(new RenderEvent.AddSystemMessage("[已结束当前会话，新会话已开启]"));
    }

    @Override public void triggerCompact() { orch.handleCompact(); }

    @Override
    public void openSessionList() {
        if (terminal == null || projectSessionsDir == null) {
            orch.safePut(new RenderEvent.AddSystemMessage("[会话恢复功能不可用]"));
            orch.safePut(new RenderEvent.FinalizeMessage());
            return;
        }
        String blocked = ResumeGate.check(orch.isAgentRunning(), orch.isResuming());
        if (blocked != null) {
            orch.safePut(new RenderEvent.AddSystemMessage("[" + blocked + "]"));
            orch.safePut(new RenderEvent.FinalizeMessage());
            return;
        }
        try {
            var items = com.lavendercode.chat.session.SessionCatalog.list(projectSessionsDir);
            if (items.isEmpty()) {
                orch.safePut(new RenderEvent.AddSystemMessage("[没有可恢复的会话]"));
                orch.safePut(new RenderEvent.FinalizeMessage());
                return;
            }
            var selected = SessionPicker.pick(terminal, items);
            if (selected == null) {
                orch.safePut(new RenderEvent.AddSystemMessage("[已取消恢复会话]"));
                orch.safePut(new RenderEvent.FinalizeMessage());
                return;
            }
            orch.handleResumeSession(selected.sessionId());
        } catch (IOException e) {
            orch.safePut(new RenderEvent.AddSystemMessage("[读取会话列表失败: " + e.getMessage() + "]"));
            orch.safePut(new RenderEvent.FinalizeMessage());
        }
    }

    @Override public void shutdown() { orch.handleShutdown(); }
    @Override public void injectUserMessage(String text) { orch.handleSendMessage(new InputEvent.SendMessage(text)); }
}