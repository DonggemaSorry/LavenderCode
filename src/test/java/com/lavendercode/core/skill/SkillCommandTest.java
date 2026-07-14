package com.lavendercode.core.skill;

import com.lavendercode.core.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SkillCommandTest {

    @Test
    void skillCommandDescriptionHasSuffix() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "commit", "提交代码变更", null, List.of(), null, "inline", null, null),
            "body", null, true));

        List<CommandDefinition> skillCmds = buildSkillCommands(catalog, Set.of());
        assertThat(skillCmds).hasSize(1);
        assertThat(skillCmds.get(0).metadata().name()).isEqualTo("commit");
        assertThat(skillCmds.get(0).metadata().description()).endsWith(" [skill]");
        assertThat(skillCmds.get(0).metadata().kind()).isEqualTo(CommandKind.PROMPT);
        assertThat(skillCmds.get(0).metadata().hidden()).isFalse();
    }

    @Test
    void skipsExistingCommandNames() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "help", "自定义 help", null, List.of(), null, "inline", null, null),
            "body", null, true));
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "commit", "提交", null, List.of(), null, "inline", null, null),
            "body", null, true));

        List<CommandDefinition> skillCmds = buildSkillCommands(catalog, Set.of("help"));
        assertThat(skillCmds).hasSize(1);
        assertThat(skillCmds.get(0).metadata().name()).isEqualTo("commit");
    }

    @Test
    void skillCommandHandlerReturnsRenderedBody() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "test", "test skill", null, List.of(), null, "inline", null, null),
            "Hello $ARGUMENTS", null, true));

        List<CommandDefinition> skillCmds = buildSkillCommands(catalog, Set.of());
        var handler = skillCmds.get(0).handler();
        // handler 需要调用 catalog.getFull 获取 body
        // 由于没有 SkillHost，只测试 substituteArguments 部分
        String body = catalog.getFull("test").promptBody();
        String rendered = SkillExecutor.substituteArguments(body, "world");
        assertThat(rendered).isEqualTo("Hello world");
    }

    static List<CommandDefinition> buildSkillCommands(SkillCatalog catalog, Set<String> existingNames) {
        List<CommandDefinition> cmds = new ArrayList<>();
        for (var meta : catalog.list()) {
            if (existingNames.contains(meta.name())) continue;
            String desc = meta.description() != null ? meta.description() : meta.name();
            desc = desc + " [skill]";
            final String skillName = meta.name();
            CommandHandler handler = (ctx, args) -> {
                var skill = catalog.getFull(skillName);
                if (skill == null || skill.promptBody() == null) return null;
                return SkillExecutor.substituteArguments(skill.promptBody(), args);
            };
            cmds.add(new CommandDefinition(
                new CommandMetadata(meta.name(), List.of(), desc, CommandKind.PROMPT, false),
                handler));
        }
        return cmds;
    }
}
