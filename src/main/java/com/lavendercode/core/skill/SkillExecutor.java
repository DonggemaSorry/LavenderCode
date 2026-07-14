package com.lavendercode.core.skill;

import com.lavendercode.core.provider.Message;
import java.util.List;
import java.util.function.Predicate;

public final class SkillExecutor {

    private SkillExecutor() {}

    public static String substituteArguments(String body, String args) {
        if (args == null || args.isBlank()) return body;
        if (body.contains("$ARGUMENTS")) return body.replace("$ARGUMENTS", args);
        return body + "\n\n## User Request\n" + args;
    }

    static List<Message> buildForkSeed(String forkContext, List<Message> parent) {
        if (parent == null || parent.isEmpty()) return List.of();
        return switch (forkContext) {
            case "full" -> List.copyOf(parent);
            case "recent" -> {
                int from = Math.max(0, parent.size() - 5);
                yield List.copyOf(parent.subList(from, parent.size()));
            }
            default -> List.of();
        };
    }

    static void assertAllowedToolsExist(List<String> allowedTools, SkillHost host) {
        if (allowedTools == null || allowedTools.isEmpty()) return;
        for (String toolName : allowedTools) {
            if (!host.hasTool(toolName)) {
                throw new IllegalStateException(
                    "工具 '" + toolName + "' 未注册，无法激活技能");
            }
        }
    }

    // --- executeInline ---

    public static String executeInline(SkillCatalog.Skill skill, String args, SkillHost host) {
        assertAllowedToolsExist(skill.meta().allowedTools(), host);
        String body = skill.promptBody();
        String rendered = substituteArguments(body, args);
        host.activateSkill(skill.meta().name(), rendered);
        if (skill.meta().allowedTools() != null) {
            host.setToolFilter(name -> skill.meta().allowedTools().contains(name));
        }
        return rendered;
    }

    // --- executeFork ---

    public static String executeFork(SkillCatalog.Skill skill, String args, SkillForkHost host) {
        assertAllowedToolsExist(skill.meta().allowedTools(), host);
        String body = skill.promptBody();
        String rendered = substituteArguments(body, args);
        List<Message> seed = buildForkSeed(skill.meta().forkContext(),
                                            host.snapshotParentMessages());
        return host.runSubAgent(rendered, seed, skill.meta().allowedTools(),
                                skill.meta().model());
    }
}
