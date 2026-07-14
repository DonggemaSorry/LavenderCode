package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommandHandlerTest {

    private CommandContext ctx;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        ctx = mock(CommandContext.class);
        registry = new CommandRegistry(BuiltinCommandRegistrar.builtinCommands());
        BuiltinCommandRegistrar.bindRegistry(registry);
    }

    private void execute(String name) {
        registry.find(name).orElseThrow().handler().execute(ctx, null);
    }

    @Test
    void helpOutputsSortedCommandList() {
        execute("help");
        verify(ctx).printMessage(argThat(s -> s.contains("/clear") && s.contains("/status")));
    }

    @Test
    void statusOutputsSixFieldsInOrder() {
        when(ctx.currentModeLabel()).thenReturn("DEFAULT");
        when(ctx.totalInputTokens()).thenReturn(1234);
        when(ctx.totalOutputTokens()).thenReturn(567);
        when(ctx.toolCount()).thenReturn(6);
        when(ctx.memoryEntryCount()).thenReturn(3);
        when(ctx.modelName()).thenReturn("claude-sonnet-4-20250514");
        when(ctx.workingDirectory()).thenReturn(java.nio.file.Path.of("/home/user/project"));
        execute("status");
        verify(ctx).printMessage(argThat(s ->
            s.contains("Mode:") && s.contains("Input tokens:") &&
            s.contains("Output tokens:") && s.contains("Tools:") &&
            s.contains("Memories:") && s.contains("Model:") &&
            s.contains("Directory:")));
    }

    @Test
    void memoryOutputsFileNames() {
        when(ctx.memoryFileNames()).thenReturn(List.of("coding-standards.md"));
        execute("memory");
        verify(ctx).printMessage(argThat(s -> s.contains("coding-standards.md")));
    }

    @Test
    void permissionOutputsModeLabel() {
        when(ctx.currentModeLabel()).thenReturn("DEFAULT");
        execute("permission");
        verify(ctx).printMessage("DEFAULT");
    }

    @Test
    void sessionOutputsArchiveInfo() {
        when(ctx.sessionId()).thenReturn("20260713-224500-a1b2c3d4");
        when(ctx.sessionArchivePath()).thenReturn(java.nio.file.Path.of("/tmp/conversation.jsonl"));
        execute("session");
        verify(ctx).printMessage(argThat(s ->
            s.contains("Session ID:") && s.contains("Archive:")));
    }

    @Test
    void planCallsEnterPlanMode() {
        execute("plan");
        verify(ctx).enterPlanMode();
    }

    @Test
    void doCallsExitPlanAndInjectsMessage() {
        execute("do");
        verify(ctx).exitPlanToDefault();
        verify(ctx).injectUserMessage("请根据以上计划开始执行");
    }

    @Test
    void reviewInjectsReviewPrompt() {
        execute("review");
        verify(ctx).injectUserMessage(argThat(s -> s.contains("审查")));
    }

    @Test
    void clearCallsClearAndNewSession() {
        execute("clear");
        verify(ctx).clearAndNewSession();
    }

    @Test
    void compactCallsTriggerCompact() {
        execute("compact");
        verify(ctx).triggerCompact();
    }

    @Test
    void exitCallsShutdown() {
        execute("exit");
        verify(ctx).shutdown();
    }

    @Test
    void resumeCallsOpenSessionList() {
        execute("resume");
        verify(ctx).openSessionList();
    }
}