package com.lavendercode.core.skill;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.*;

class SkillExecutorTest {

    private SkillCatalog.SkillMeta meta(String name, String mode, List<String> allowedTools, String forkContext) {
        return SkillCatalog.SkillMeta.withDefaults(
            name, "desc", null, List.of(), allowedTools, mode, null, forkContext);
    }

    private SkillCatalog.Skill skill(String name, String mode, String body, List<String> allowedTools, String forkContext) {
        return new SkillCatalog.Skill(meta(name, mode, allowedTools, forkContext), body, null, true);
    }

    // --- executeInline ---

    @Test
    void executeInlineReturnsRenderedBody() {
        var skill = skill("test", "inline", "Hello $ARGUMENTS", null, null);
        var host = new RecordingHost();
        String result = SkillExecutor.executeInline(skill, "world", host);
        assertThat(result).isEqualTo("Hello world");
    }

    @Test
    void executeInlineActivatesSkill() {
        var skill = skill("commit", "inline", "Commit SOP", null, null);
        var host = new RecordingHost();
        SkillExecutor.executeInline(skill, null, host);
        assertThat(host.activatedName).isEqualTo("commit");
        assertThat(host.activatedBody).isEqualTo("Commit SOP");
    }

    @Test
    void executeInlineSetsToolFilterWhenAllowedToolsNotNull() {
        var skill = skill("review", "inline", "body", List.of("read_file", "grep"), null);
        var host = new RecordingHost();
        SkillExecutor.executeInline(skill, null, host);
        assertThat(host.filterSet).isTrue();
    }

    @Test
    void executeInlineDoesNotSetFilterWhenAllowedToolsNull() {
        var skill = skill("review", "inline", "body", null, null);
        var host = new RecordingHost();
        SkillExecutor.executeInline(skill, null, host);
        assertThat(host.filterSet).isFalse();
    }

    @Test
    void executeInlineThrowsWhenToolMissing() {
        var skill = skill("bad", "inline", "body", List.of("nonexistent"), null);
        var host = new RecordingHost(n -> false);
        assertThatThrownBy(() -> SkillExecutor.executeInline(skill, null, host))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void executeInlineAppendsArgsWhenNoPlaceholder() {
        var skill = skill("test", "inline", "SOP body", null, null);
        var host = new RecordingHost();
        String result = SkillExecutor.executeInline(skill, "do thing", host);
        assertThat(result).isEqualTo("SOP body\n\n## User Request\ndo thing");
    }

    // --- executeFork ---

    @Test
    void executeForkReturnsSubAgentResult() {
        var skill = skill("fork-skill", "fork", "Fork SOP", null, "none");
        var host = new RecordingForkHost("sub-agent result");
        String result = SkillExecutor.executeFork(skill, null, host);
        assertThat(result).isEqualTo("sub-agent result");
    }

    @Test
    void executeForkPassesRenderedBody() {
        var skill = skill("fork-skill", "fork", "SOP: $ARGUMENTS", null, "none");
        var host = new RecordingForkHost("result");
        SkillExecutor.executeFork(skill, "args here", host);
        assertThat(host.passedBody).isEqualTo("SOP: args here");
    }

    @Test
    void executeForkPassesAllowedTools() {
        var skill = skill("fork-skill", "fork", "body", List.of("read_file"), "none");
        var host = new RecordingForkHost("result");
        SkillExecutor.executeFork(skill, null, host);
        assertThat(host.passedAllowedTools).containsExactly("read_file");
    }

    @Test
    void executeForkPassesModel() {
        var skill = new SkillCatalog.Skill(
            SkillCatalog.SkillMeta.withDefaults(
                "fork-skill", "desc", null, List.of(), null, "fork", "claude-3-opus", "none"),
            "body", null, true);
        var host = new RecordingForkHost("result");
        SkillExecutor.executeFork(skill, null, host);
        assertThat(host.passedModel).isEqualTo("claude-3-opus");
    }

    @Test
    void executeForkBuildsSeedFromForkContext() {
        var parentMsgs = List.of(
            new Message(Role.USER, "msg0"),
            new Message(Role.USER, "msg1"),
            new Message(Role.USER, "msg2"),
            new Message(Role.USER, "msg3"),
            new Message(Role.USER, "msg4"),
            new Message(Role.USER, "msg5"),
            new Message(Role.USER, "msg6"));
        var skill = skill("fork-skill", "fork", "body", null, "recent");
        var host = new RecordingForkHost("result", parentMsgs);
        SkillExecutor.executeFork(skill, null, host);
        assertThat(host.passedSeed).hasSize(5);
        assertThat(host.passedSeed.get(0).content()).isEqualTo("msg2");
    }

    // --- Helper classes ---

    private static class RecordingHost implements SkillHost {
        String activatedName;
        String activatedBody;
        boolean filterSet = false;
        private final Predicate<String> hasToolFn;

        RecordingHost() { this(n -> true); }
        RecordingHost(Predicate<String> hasToolFn) { this.hasToolFn = hasToolFn; }

        @Override public void activateSkill(String name, String body) {
            activatedName = name;
            activatedBody = body;
        }
        @Override public void setToolFilter(Predicate<String> filter) { filterSet = true; }
        @Override public boolean hasTool(String name) { return hasToolFn.test(name); }
    }

    private static class RecordingForkHost implements SkillForkHost {
        final String subResult;
        final List<Message> parentMsgs;
        String passedBody;
        List<Message> passedSeed;
        List<String> passedAllowedTools;
        String passedModel;
        boolean filterSet = false;
        String activatedName;

        RecordingForkHost(String subResult) { this(subResult, List.of()); }
        RecordingForkHost(String subResult, List<Message> parentMsgs) {
            this.subResult = subResult;
            this.parentMsgs = parentMsgs;
        }

        @Override public void activateSkill(String name, String body) { activatedName = name; }
        @Override public void setToolFilter(Predicate<String> filter) { filterSet = true; }
        @Override public boolean hasTool(String name) { return true; }
        @Override
        public String runSubAgent(String body, List<Message> seed,
                                   List<String> allowedTools, String model) {
            passedBody = body;
            passedSeed = seed;
            passedAllowedTools = allowedTools;
            passedModel = model;
            return subResult;
        }
        @Override public List<Message> snapshotParentMessages() { return parentMsgs; }
    }
}
