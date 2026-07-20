package com.lavendercode.core.team;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** 队员 Loop 每轮 LLM 前拉取未读邮箱的钩子（ThreadLocal）。 */
public final class IncomingMailHook {
    private static final ThreadLocal<Supplier<Optional<String>>> TL = new ThreadLocal<>();

    private IncomingMailHook() {}

    public static void set(Supplier<Optional<String>> supplier) {
        TL.set(supplier);
    }

    public static void clear() {
        TL.remove();
    }

    public static Optional<String> poll() {
        Supplier<Optional<String>> s = TL.get();
        if (s == null) {
            return Optional.empty();
        }
        Optional<String> v = s.get();
        return v == null ? Optional.empty() : v;
    }

    public static Supplier<Optional<String>> forTeammate(Team team, String agentId) {
        Mailbox mailbox = new Mailbox(team.configDir());
        return () -> {
            List<MailMessage> unread = mailbox.claimUnread(agentId);
            if (unread.isEmpty()) {
                return Optional.empty();
            }
            String formatted = IncomingMessageFormatter.format(unread);
            // Plan 审批副作用：收到 approve 时切 DEFAULT（via TeammateContext side channel）
            for (MailMessage m : unread) {
                if ("plan_approval_response".equals(m.type()) && m.payload() instanceof java.util.Map<?, ?> map) {
                    Object approve = map.get("approve");
                    if (Boolean.TRUE.equals(approve) || "true".equalsIgnoreCase(String.valueOf(approve))) {
                        PlanApprovalState.markApproved();
                    } else {
                        PlanApprovalState.markRejected(String.valueOf(map.get("feedback")));
                    }
                }
            }
            return Optional.of(formatted);
        };
    }
}
