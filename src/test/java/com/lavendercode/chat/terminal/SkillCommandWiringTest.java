package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.CommandKind;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.skill.SkillCatalog;
import com.lavendercode.core.skill.SkillForkHost;
import com.lavendercode.core.skill.SkillHost;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class SkillCommandWiringTest {

    @Test
    void forkModeCallsExecuteForkNotPromptInject() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "deep", "deep explore", null, List.of(), List.of("read_file"),
                "fork", null, "none"),
            "Explore $ARGUMENTS", null, true));

        AtomicReference<String> forkBody = new AtomicReference<>();
        SkillForkHost forkHost = recordingForkHost(forkBody);
        SkillHost skillHost = recordingSkillHost();

        var cmds = SkillCommandWiring.buildSkillCommands(
            catalog, Set.of(), skillHost, forkHost);
        assertThat(cmds).hasSize(1);

        String returned = cmds.get(0).handler().execute(printingCtx(), "the codebase");
        assertThat(returned).isNull();
        assertThat(forkBody.get()).contains("Explore the codebase");
    }

    @Test
    void inlineModeReturnsRenderedPrompt() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "commit", "commit help", null, List.of(), null,
                "inline", null, null),
            "Commit $ARGUMENTS", null, true));

        var cmds = SkillCommandWiring.buildSkillCommands(
            catalog, Set.of(), recordingSkillHost(), recordingForkHost(new AtomicReference<>()));
        String returned = cmds.get(0).handler().execute(printingCtx(), "fix");
        assertThat(returned).isEqualTo("Commit fix");
    }

    @Test
    void descriptionHasSkillSuffix() {
        var catalog = new SkillCatalog();
        catalog.register(new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "x", "desc", null, List.of(), null, "inline", null, null),
            "body", null, true));
        var cmds = SkillCommandWiring.buildSkillCommands(
            catalog, Set.of(), recordingSkillHost(), recordingForkHost(new AtomicReference<>()));
        assertThat(cmds.get(0).metadata().description()).endsWith(" [skill]");
        assertThat(cmds.get(0).metadata().kind()).isEqualTo(CommandKind.PROMPT);
    }

    private static SkillHost recordingSkillHost() {
        return new SkillHost() {
            public void activateSkill(String name, String body) {}
            public void setToolFilter(java.util.function.Predicate<String> filter) {}
            public boolean hasTool(String name) { return true; }
        };
    }

    private static SkillForkHost recordingForkHost(AtomicReference<String> forkBody) {
        return new SkillForkHost() {
            public void activateSkill(String name, String body) {}
            public void setToolFilter(java.util.function.Predicate<String> filter) {}
            public boolean hasTool(String name) { return true; }
            public String runSubAgent(String body, List<Message> seed,
                                      List<String> allowedTools, String model) {
                forkBody.set(body);
                return "fork-ok";
            }
            public List<Message> snapshotParentMessages() { return List.of(); }
        };
    }

    private static com.lavendercode.core.command.CommandContext printingCtx() {
        return new com.lavendercode.core.command.CommandContext() {
            public String currentModeLabel() { return "default"; }
            public int totalInputTokens() { return 0; }
            public int totalOutputTokens() { return 0; }
            public int toolCount() { return 0; }
            public int memoryEntryCount() { return 0; }
            public String modelName() { return "m"; }
            public Path workingDirectory() { return Path.of("."); }
            public List<String> memoryFileNames() { return List.of(); }
            public String sessionId() { return ""; }
            public Path sessionArchivePath() { return Path.of("."); }
            public void printMessage(String text) {}
            public void enterPlanMode() {}
            public void exitPlanToDefault() {}
            public void clearAndNewSession() {}
            public void triggerCompact() {}
            public void openSessionList() {}
            public void shutdown() {}
            public void injectUserMessage(String text) {}
            public String hookRules() { return ""; }
        };
    }
}
