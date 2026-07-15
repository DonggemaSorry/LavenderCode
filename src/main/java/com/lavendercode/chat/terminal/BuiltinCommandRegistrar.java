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
            def("clear",   List.of(),       "清空对话并开启新会话",     CommandKind.UI,     (ctx, args) -> { ctx.clearAndNewSession(); return null; }),
            def("compact", List.of(),       "手动压缩上下文",           CommandKind.UI,     (ctx, args) -> { ctx.triggerCompact(); return null; }),
            def("do",      List.of(),       "退出计划模式并开始执行",    CommandKind.PROMPT, (ctx, args) -> { ctx.exitPlanToDefault(); ctx.injectUserMessage("请根据以上计划开始执行"); return null; }),
            def("exit",    List.of("quit"), "退出 LavenderCode",        CommandKind.UI,     (ctx, args) -> { ctx.shutdown(); return null; }),
            def("help",    List.of(),       "显示可用命令列表",         CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(formatHelp()); return null; }),
            def("hooks",   List.of(),       "显示已加载的 Hook 规则列表", CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(ctx.hookRules()); return null; }),
            def("memory",  List.of(),       "显示已加载的记忆文件列表",  CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(formatMemory(ctx)); return null; }),
            def("permission", List.of(),    "显示当前权限模式",         CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(ctx.currentModeLabel()); return null; }),
            def("plan",    List.of(),       "进入计划模式（仅只读工具）", CommandKind.UI,     (ctx, args) -> { ctx.enterPlanMode(); return null; }),
            def("resume",  List.of(),       "从历史会话恢复",           CommandKind.UI,     (ctx, args) -> { ctx.openSessionList(); return null; }),
            def("review",  List.of(),       "请求 AI 审查当前代码变更",  CommandKind.PROMPT, (ctx, args) -> { ctx.injectUserMessage(REVIEW_PROMPT); return null; }),
            def("session", List.of(),       "显示当前会话信息",         CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(formatSession(ctx)); return null; }),
            def("status",  List.of(),       "显示系统状态信息",         CommandKind.LOCAL,  (ctx, args) -> { ctx.printMessage(formatStatus(ctx)); return null; }),
            def("worktree", List.of(),     "管理 Git Worktree 隔离目录", CommandKind.LOCAL, BuiltinCommandRegistrar::handleWorktree)
        );
    }

    static String handleWorktree(CommandContext ctx, String args) {
        var mgr = ctx.worktreeManager();
        if (mgr == null) {
            ctx.printMessage("Worktree 功能未启用（当前目录不是可用的 git 仓库根）");
            return null;
        }
        String[] parts = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        if (parts.length == 0) {
            ctx.printMessage("用法: /worktree create <slug> | list | enter <slug> | exit [--remove] [--discard] | remove <slug> [--discard]");
            return null;
        }
        try {
            switch (parts[0]) {
                case "create" -> {
                    if (parts.length < 2) {
                        ctx.printMessage("用法: /worktree create <slug>");
                        return null;
                    }
                    var wt = mgr.create(parts[1], "HEAD", true);
                    ctx.printMessage("已创建 Worktree\n  name: " + wt.name()
                        + "\n  path: " + wt.path()
                        + "\n  branch: " + wt.branch());
                }
                case "list" -> {
                    var list = mgr.list();
                    if (list.isEmpty()) {
                        ctx.printMessage("(无 Worktree)");
                        return null;
                    }
                    String active = ctx.activeCwd() != null ? ctx.activeCwd().toString() : null;
                    var sb = new StringBuilder();
                    for (var wt : list) {
                        boolean isActive = active != null
                            && wt.path().toAbsolutePath().normalize().toString().equals(
                                java.nio.file.Path.of(active).toAbsolutePath().normalize().toString());
                        sb.append(wt.name()).append("  ").append(wt.path())
                            .append("  ").append(wt.branch())
                            .append(isActive ? "  [active]" : "").append('\n');
                    }
                    ctx.printMessage(sb.toString().stripTrailing());
                }
                case "enter" -> {
                    if (parts.length < 2) {
                        ctx.printMessage("用法: /worktree enter <slug>");
                        return null;
                    }
                    var session = mgr.enter(parts[1]);
                    ctx.setActiveCwd(session.worktreePath());
                    ctx.printMessage("已进入 Worktree: " + session.worktreePath());
                }
                case "exit" -> {
                    var session = mgr.currentSession();
                    if (session == null) {
                        ctx.printMessage("当前没有活跃的 Worktree session");
                        return null;
                    }
                    boolean remove = false;
                    boolean discard = false;
                    for (int i = 1; i < parts.length; i++) {
                        if ("--remove".equals(parts[i])) remove = true;
                        if ("--discard".equals(parts[i])) discard = true;
                    }
                    var action = remove
                        ? com.lavendercode.core.worktree.ExitAction.REMOVE
                        : com.lavendercode.core.worktree.ExitAction.KEEP;
                    var opts = discard
                        ? com.lavendercode.core.worktree.ExitOptions.discard()
                        : com.lavendercode.core.worktree.ExitOptions.keepSafe();
                    var report = mgr.exit(session.worktreeName(), action, opts);
                    ctx.setActiveCwd(null);
                    ctx.printMessage(report.removed()
                        ? "已退出并删除 Worktree: " + report.path()
                        : "已退出 Worktree（保留）: " + report.path());
                }
                case "remove" -> {
                    if (parts.length < 2) {
                        ctx.printMessage("用法: /worktree remove <slug> [--discard]");
                        return null;
                    }
                    boolean discard = false;
                    for (int i = 2; i < parts.length; i++) {
                        if ("--discard".equals(parts[i])) discard = true;
                    }
                    mgr.remove(parts[1], discard
                        ? com.lavendercode.core.worktree.ExitOptions.discard()
                        : com.lavendercode.core.worktree.ExitOptions.keepSafe());
                    if (ctx.activeCwd() != null) {
                        // if removing active, clear
                        ctx.setActiveCwd(null);
                    }
                    ctx.printMessage("已删除 Worktree: " + parts[1]);
                }
                default -> ctx.printMessage("未知子命令: " + parts[0]);
            }
        } catch (com.lavendercode.core.worktree.WorktreeHasChangesException e) {
            ctx.printMessage("错误: " + e.getMessage() + "（可用 --discard 强制删除）");
        } catch (Exception e) {
            ctx.printMessage("错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        return null;
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