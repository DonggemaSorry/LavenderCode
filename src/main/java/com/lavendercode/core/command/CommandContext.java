package com.lavendercode.core.command;

import java.nio.file.Path;
import java.util.List;

public interface CommandContext {
    String currentModeLabel();
    int totalInputTokens();
    int totalOutputTokens();
    int toolCount();
    int memoryEntryCount();
    String modelName();
    Path workingDirectory();
    List<String> memoryFileNames();
    String sessionId();
    Path sessionArchivePath();
    void printMessage(String text);
    void enterPlanMode();
    void exitPlanToDefault();
    void clearAndNewSession();
    void triggerCompact();
    void openSessionList();
    void shutdown();
    void injectUserMessage(String text);
}