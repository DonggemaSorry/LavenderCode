package com.lavendercode.core.coordinator;

import com.lavendercode.core.config.Options;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class Coordinator {
    public static final List<String> ALLOWED_TOOLS = List.of(
        "Agent", "TeamCreate", "TeamDelete",
        "TeamTaskCreate", "TeamTaskGet", "TeamTaskList", "TeamTaskUpdate",
        "TeamSendMessage",
        "read_file", "search_file", "search_content", "execute_command");

    public static final String SYSTEM_PROMPT_SUFFIX = """

        ## Coordinator 模式纪律
        你是 Team Lead，专注 Research / Synthesis / Implementation / Verification 四阶段协调。
        派出队员或发送消息后，禁止立刻自己用 read_file/search_file/search_content/execute_command 重复探索；
        禁止用 sleep 或 TaskList 轮询凑时间。等待队员汇报（邮箱/任务通知）后再继续。
        派出后本轮只应用一行总结「已派 N 名队员探索 X，等结果」并结束本轮。
        允许自己使用读类工具的场景仅限：Research 初次定位目标；Synthesis 读队员产出报告；Verification 做 git diff/status 等收敛。
        收敛用 execute_command 执行 git merge；搞不定则 git merge --abort 并上报用户，保留队员 worktree。
        """;

    private Coordinator() {}

    public static boolean isEnabled(Options opt) {
        return isEnabled(opt, System::getenv);
    }

    public static boolean isEnabled(Options opt, Function<String, String> env) {
        if (opt == null || !Boolean.TRUE.equals(opt.coordinatorMode())) {
            return false;
        }
        return envTruthy(env.apply("LAVENDERCODE_COORDINATOR_MODE"));
    }

    public static boolean envTruthy(String v) {
        if (v == null || v.isBlank()) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return "1".equals(s) || "true".equals(s) || "yes".equals(s);
    }
}
