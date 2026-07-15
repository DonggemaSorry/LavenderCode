package com.lavendercode.core.tool;

import com.lavendercode.core.subagent.*;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.tool.ToolContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentTool implements Tool {

    private final SubAgentServices services;
    private final ScheduledExecutorService timeoutScheduler;

    public AgentTool(SubAgentServices services) {
        this(services, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "subagent-timeout");
            t.setDaemon(true);
            return t;
        }));
    }

    AgentTool(SubAgentServices services, ScheduledExecutorService timeoutScheduler) {
        this.services = services;
        this.timeoutScheduler = timeoutScheduler;
    }

    @Override
    public String name() {
        return "Agent";
    }

    @Override
    public String description() {
        return "Launch a sub-agent to perform a task in an isolated context. "
            + "Use subagent_type for predefined roles (explore, plan, general-purpose) "
            + "or omit for Fork mode.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema(
            "object",
            Map.of(
                "prompt", new ToolParameterSchema.PropertyDef(
                    "string", "Task instruction for the sub-agent", null, null),
                "description", new ToolParameterSchema.PropertyDef(
                    "string", "Short description for UI", null, null),
                "subagent_type", new ToolParameterSchema.PropertyDef(
                    "string", "Predefined role name; omit for Fork", null, null),
                "model", new ToolParameterSchema.PropertyDef(
                    "string", "Model override: haiku, sonnet, opus, inherit",
                    List.of("haiku", "sonnet", "opus", "inherit"), null),
                "run_in_background", new ToolParameterSchema.PropertyDef(
                    "boolean", "Run in background", null, null),
                "name", new ToolParameterSchema.PropertyDef(
                    "string", "Optional name for SendMessage", null, null)),
            List.of("prompt", "description"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (SubAgentCallContext.current() == SubAgentCallContext.Kind.FORK) {
            return ToolResult.error(
                "NESTED_AGENT", "Fork 子 Agent 不能再启动 Agent", "");
        }

        List<Message> parentConv = services.parentMessages();
        if (ForkMessageBuilder.historyContainsBoilerplate(parentConv)) {
            return ToolResult.error(
                "NESTED_AGENT", "Fork 子 Agent 不能再启动 Agent", "");
        }

        String prompt = stringParam(params, "prompt");
        String description = stringParam(params, "description");
        if (prompt == null || prompt.isBlank()) {
            return ToolResult.error("VALIDATION", "prompt 必填", "");
        }
        if (description == null || description.isBlank()) {
            return ToolResult.error("VALIDATION", "description 必填", "");
        }

        String subagentType = stringParam(params, "subagent_type");
        boolean fork = subagentType == null || subagentType.isBlank();
        boolean runInBackground = Boolean.TRUE.equals(params.get("run_in_background"));
        String nameParam = stringParam(params, "name");
        final String taskName = (nameParam == null || nameParam.isBlank()) ? description : nameParam;

        boolean backgroundEnabled = services.options() == null
            || Boolean.TRUE.equals(services.options().enableSubAgentBackground());

        if (!backgroundEnabled && (fork || runInBackground)) {
            return ToolResult.error(
                "BACKGROUND_DISABLED",
                "enableSubAgentBackground=false 时不支持 Fork 或后台运行",
                "");
        }

        AgentDefinition def;
        final List<Message> seed;
        if (fork) {
            def = AgentDefinition.forkBase(ForkBoilerplate.format(prompt));
            seed = ForkMessageBuilder.build(parentConv, prompt);
        } else {
            def = services.catalog().resolve(subagentType.trim());
            if (def == null) {
                return ToolResult.error("UNKNOWN_TYPE", "未知 subagent_type: " + subagentType, "");
            }
            seed = null;
        }

        boolean forceBackground = fork || def.forceBackground() || runInBackground;
        if ("worktree".equals(def.isolation())) {
            forceBackground = false; // F23：隔离本期强制前台
        }
        SubAgentCallContext.Kind kind = fork ? SubAgentCallContext.Kind.FORK : SubAgentCallContext.Kind.DEFINED;

        boolean useWorktree = "worktree".equals(def.isolation());
        final boolean bg = forceBackground;
        return SubAgentCallContext.run(kind, () -> {
            if (useWorktree) {
                return executeWithWorktree(def, prompt, description, taskName, fork, seed);
            }
            return executeLaunch(def, prompt, description, taskName, fork, bg, seed);
        });
    }

    private ToolResult executeWithWorktree(AgentDefinition def, String prompt, String description,
                                           String taskName, boolean fork, List<Message> seed) {
        var mgr = services.worktreeManager();
        if (mgr == null) {
            return ToolResult.error("WORKTREE_DISABLED", "Worktree 功能未启用", "");
        }
        String name = "agent-a" + String.format("%07x",
            java.util.concurrent.ThreadLocalRandom.current().nextInt(0x10000000));
        try {
            var wt = mgr.create(name, "HEAD", false);
            String notice = com.lavendercode.core.worktree.WorktreeNotice.build(
                services.projectRoot(), wt.path());
            String task = notice + "\n" + prompt;
            ToolContext ctx = ToolContext.empty().withCwd(wt.path());
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            AtomicReference<List<Message>> conversationOut = new AtomicReference<>();
            Callable<String> work = SubAgentLauncher.buildWork(
                services, def, task, fork, false, seed, cancelFlag, conversationOut, ctx);
            ToolResult result = runInlineWithAdopt(work, def, task, description, taskName,
                cancelFlag, conversationOut);
            var cleanup = mgr.autoCleanup(name);
            if (cleanup.kept() && result.success()) {
                String appended = (result.content() == null ? "" : result.content())
                    + "\n[Worktree 保留在 " + cleanup.path() + ",分支 " + cleanup.branch() + "]";
                return ToolResult.success(description, appended);
            }
            return result;
        } catch (Exception e) {
            return ToolResult.error("WORKTREE_ERROR",
                e.getMessage() != null ? e.getMessage() : "Worktree 启动失败", "");
        }
    }

    private ToolResult executeLaunch(AgentDefinition def, String prompt, String description,
                                     String taskName, boolean fork, boolean forceBackground,
                                     List<Message> seed) {
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        AtomicReference<List<Message>> conversationOut = new AtomicReference<>();
        Callable<String> work = SubAgentLauncher.buildWork(
            services, def, prompt, fork, forceBackground, seed, cancelFlag, conversationOut);

        if (forceBackground) {
            if (services.taskManager() == null) {
                return ToolResult.error("NO_TASK_MANAGER", "TaskManager 未配置", "");
            }
            String taskId = services.taskManager().launch(work, taskName, seed, conversationOut);
            return ToolResult.success(description,
                "{\"task_id\":\"" + taskId + "\",\"status\":\"async_launched\"}");
        }

        return runInlineWithAdopt(work, def, prompt, description, taskName, cancelFlag, conversationOut);
    }

    private ToolResult runInlineWithAdopt(Callable<String> work, AgentDefinition def,
                                          String prompt, String description, String taskName,
                                          AtomicBoolean cancelFlag,
                                          AtomicReference<List<Message>> conversationOut) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        ScheduledFuture<?> timeoutFuture = null;
        if (services.foregroundTracker() != null) {
            timeoutFuture = timeoutScheduler.schedule(() -> {
                if (!future.isDone() && services.taskManager() != null) {
                    services.foregroundTracker().adoptToBackground(services.taskManager(), taskName);
                }
            }, SubAgentConstants.AUTO_BACKGROUND_MS, TimeUnit.MILLISECONDS);

            services.foregroundTracker().register(
                new ForegroundSubAgentTracker.ForegroundRun(
                    description, def, prompt, cancelFlag, future, timeoutFuture, conversationOut));
        }

        try {
            String result = future.get(SubAgentConstants.AUTO_BACKGROUND_MS, TimeUnit.MILLISECONDS);
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
            if (services.foregroundTracker() != null) {
                services.foregroundTracker().clear();
            }
            return ToolResult.success(description, result);
        } catch (TimeoutException e) {
            if (services.taskManager() != null && services.foregroundTracker() != null) {
                String taskId = services.foregroundTracker().adoptToBackground(
                    services.taskManager(), taskName);
                if (taskId != null) {
                    return ToolResult.success(description,
                        "{\"task_id\":\"" + taskId + "\",\"status\":\"timed_out_to_background\"}");
                }
            }
            return ToolResult.error("TIMEOUT", "子 Agent 执行超时", "");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return ToolResult.error("SUBAGENT_ERROR", cause.getMessage(), "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ToolResult.error("INTERRUPTED", "子 Agent 被中断", "");
        } finally {
            if (services.foregroundTracker() != null) {
                services.foregroundTracker().clear();
            }
        }
    }

    private static String stringParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
