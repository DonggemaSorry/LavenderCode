package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.CommandDefinition;
import com.lavendercode.core.command.CommandHandler;
import com.lavendercode.core.command.CommandKind;
import com.lavendercode.core.command.CommandMetadata;
import com.lavendercode.core.skill.SkillCatalog;
import com.lavendercode.core.skill.SkillExecutor;
import com.lavendercode.core.skill.SkillForkHost;
import com.lavendercode.core.skill.SkillHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registers Skill Catalog entries as slash commands, dispatching
 * inline vs fork execution according to skill mode.
 */
final class SkillCommandWiring {

    private SkillCommandWiring() {}

    static List<CommandDefinition> buildSkillCommands(
            SkillCatalog catalog,
            Set<String> existingNames,
            SkillHost skillHost,
            SkillForkHost skillForkHost) {
        List<CommandDefinition> cmds = new ArrayList<>();
        if (catalog == null) {
            return cmds;
        }
        for (var meta : catalog.list()) {
            if (existingNames.contains(meta.name())) {
                continue;
            }
            String desc = (meta.description() != null ? meta.description() : meta.name()) + " [skill]";
            final String skillName = meta.name();
            CommandHandler handler = (ctx, args) -> {
                var skill = catalog.getFull(skillName);
                if (skill == null || skill.promptBody() == null) {
                    return null;
                }
                String mode = skill.meta().mode() != null ? skill.meta().mode() : "inline";
                if ("fork".equalsIgnoreCase(mode)) {
                    if (skillForkHost == null) {
                        ctx.printMessage("[Skill fork 不可用: 宿主未配置]");
                        return null;
                    }
                    String result = SkillExecutor.executeFork(skill, args, skillForkHost);
                    ctx.printMessage("[Skill fork 结果: " + skillName + "]\n" + result);
                    return null;
                }
                if (skillHost == null) {
                    return SkillExecutor.substituteArguments(skill.promptBody(), args);
                }
                return SkillExecutor.executeInline(skill, args, skillHost);
            };
            cmds.add(new CommandDefinition(
                new CommandMetadata(skillName, List.of(), desc, CommandKind.PROMPT, false),
                handler));
        }
        return cmds;
    }
}
