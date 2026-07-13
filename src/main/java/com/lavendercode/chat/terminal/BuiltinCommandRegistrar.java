package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.*;

import java.util.List;
import java.util.function.Supplier;

public final class BuiltinCommandRegistrar {

    private static final String REVIEW_PROMPT =
        "请审查当前代码库中最近发生的变更，关注潜在的错误、安全问题和代码质量。";

    private static Supplier<CommandRegistry> registrySupplier = () -> null;

    private BuiltinCommandRegistrar() {}

    public static void bindRegistry(CommandRegistry registry) {
        registrySupplier = () -> registry;
    }

    public static List<CommandDefinition> builtinCommands() {
        return List.of(
            def("clear",   List.of(),       "清空对话并开启新会话",     CommandKind.UI,     ctx -> ctx.clearAndNewSession()),
            def("compact", List.of(),       "手动压缩上下文",           CommandKind.UI,     ctx -> ctx.triggerCompact()),
            def("do",      List.of(),       "退出计划模式并开始执行",    CommandKind.PROMPT, ctx -> { ctx.exitPlanToDefault(); ctx.injectUserMessage("请根据以上计划开始执行"); }),
            def("exit",    List.of("quit"), "退出 LavenderCode",        CommandKind.UI,     ctx -> ctx.shutdown()),
            def("help",    List.of(),       "显示可用命令列表",         CommandKind.LOCAL,  ctx -> ctx.printMessage(formatHelp())),
            def("memory",  List.of(),       "显示已加载的记忆文件列表",  CommandKind.LOCAL,  ctx -> ctx.printMessage(formatMemory(ctx))),
            def("permission", List.of(),    "显示当前权限模式",         CommandKind.LOCAL,  ctx -> ctx.printMessage(ctx.currentModeLabel())),
            def("plan",    List.of(),       "进入计划模式（仅只读工具）", CommandKind.UI,     ctx -> ctx.enterPlanMode()),
            def("resume",  List.of(),       "从历史会话恢复",           CommandKind.UI,     ctx -> ctx.openSessionList()),
            def("review",  List.of(),       "请求 AI 审查当前代码变更",  CommandKind.PROMPT, ctx -> ctx.injectUserMessage(REVIEW_PROMPT)),
            def("session", List.of(),       "显示当前会话信息",         CommandKind.LOCAL,  ctx -> ctx.printMessage(formatSession(ctx))),
            def("status",  List.of(),       "显示系统状态信息",         CommandKind.LOCAL,  ctx -> ctx.printMessage(formatStatus(ctx)))
        );
    }

    static String formatHelp() {
        var reg = registrySupplier.get();
        if (reg == null) return "命令列表加载中...";
        var sb = new StringBuilder("可用命令:\n");
        int maxLen = reg.visibleCommands().stream()
            .mapToInt(d -> d.metadata().name().length())
            .max().orElse(0);
        for (var def : reg.visibleCommands()) {
            sb.append("  /")
              .append(def.metadata().name())
              .append(" ".repeat(maxLen - def.metadata().name().length() + 2))
              .append(def.metadata().description())
              .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    static String formatStatus(CommandContext ctx) {
        return """
            Mode:           %s
            Input tokens:   %s
            Output tokens:  %s
            Tools:          %d
            Memories:       %d
            Model:          %s
            Directory:      %s""".formatted(
            ctx.currentModeLabel(),
            String.format("%,d", ctx.totalInputTokens()),
            String.format("%,d", ctx.totalOutputTokens()),
            ctx.toolCount(),
            ctx.memoryEntryCount(),
            ctx.modelName(),
            ctx.workingDirectory()
        );
    }

    static String formatMemory(CommandContext ctx) {
        var files = ctx.memoryFileNames();
        if (files.isEmpty()) return "已加载记忆文件:\n  (无)";
        var sb = new StringBuilder("已加载记忆文件:\n");
        for (String name : files) {
            sb.append("  ").append(name).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    static String formatSession(CommandContext ctx) {
        var archive = ctx.sessionArchivePath();
        return """
            Session ID:     %s
            Archive:        %s""".formatted(
            ctx.sessionId(),
            archive != null ? archive.toString() : "(in-memory)"
        );
    }

    private static CommandDefinition def(String name, List<String> aliases,
                                         String desc, CommandKind kind,
                                         CommandHandler handler) {
        return new CommandDefinition(
            new CommandMetadata(name, aliases, desc, kind, false), handler);
    }
}