package com.lavendercode.chat.terminal;

import com.lavendercode.core.command.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CompletionMenuControllerTest {

    private CompletionMenuController controller;

    private static CommandRegistry twelveCommandRegistry() {
        return new CommandRegistry(List.of(
            def("clear", CommandKind.UI),    def("compact", CommandKind.UI),
            def("do", CommandKind.PROMPT),    def("exit", CommandKind.UI),
            def("help", CommandKind.LOCAL),  def("memory", CommandKind.LOCAL),
            def("permission", CommandKind.LOCAL), def("plan", CommandKind.UI),
            def("resume", CommandKind.UI),   def("review", CommandKind.PROMPT),
            def("session", CommandKind.LOCAL), def("status", CommandKind.LOCAL)
        ));
    }

    private static CommandDefinition def(String name, CommandKind kind) {
        return new CommandDefinition(
            new CommandMetadata(name, List.of(), "desc", kind, false), ctx -> {});
    }

    @BeforeEach
    void setUp() {
        controller = new CompletionMenuController(twelveCommandRegistry());
    }

    @Test
    void menuActivatesOnSlash() {
        controller.onInputChanged("/");
        var event = controller.toRenderEvent();
        assertThat(event.visible()).isTrue();
        assertThat(event.entries()).hasSize(12);
    }

    @Test
    void prefixFiltersCandidates() {
        controller.onInputChanged("/s");
        var event = controller.toRenderEvent();
        assertThat(event.entries())
            .extracting(RenderEvent.CompletionEntry::name)
            .containsExactly("session", "status");
    }

    @Test
    void prefixIsCaseInsensitive() {
        controller.onInputChanged("/S");
        var event = controller.toRenderEvent();
        assertThat(event.entries())
            .extracting(RenderEvent.CompletionEntry::name)
            .containsExactly("session", "status");
    }

    @Test
    void zeroMatchShowsEmptyMenu() {
        controller.onInputChanged("/xyz");
        var event = controller.toRenderEvent();
        assertThat(event.visible()).isTrue();
        assertThat(event.entries()).isEmpty();
    }

    @Test
    void escDismissesMenuButKeepsInput() {
        controller.onInputChanged("/s");
        controller.dismiss();
        assertThat(controller.toRenderEvent().visible()).isFalse();
    }

    @Test
    void deleteSlashClosesMenu() {
        controller.onInputChanged("/");
        controller.onInputChanged("");
        assertThat(controller.toRenderEvent().visible()).isFalse();
    }

    @Test
    void newlineClosesMenu() {
        controller.onInputChanged("/");
        controller.onInputChanged("/hel\nlo");
        assertThat(controller.toRenderEvent().visible()).isFalse();
    }

    @Test
    void navigateUpWraps() {
        controller.onInputChanged("/");
        controller.navigateUp();
        assertThat(controller.toRenderEvent().selectedIndex()).isEqualTo(11);
    }

    @Test
    void navigateDownWraps() {
        controller.onInputChanged("/");
        controller.navigateDown();
        assertThat(controller.toRenderEvent().selectedIndex()).isEqualTo(1);
    }

    @Test
    void executeSelectedReturnsCommandString() {
        controller.onInputChanged("/");
        controller.navigateDown();
        var result = controller.executeSelected();
        assertThat(result).hasValue("/compact");
    }

    @Test
    void executeSelectedOnEmptyReturnsEmpty() {
        controller.onInputChanged("/xyz");
        var result = controller.executeSelected();
        assertThat(result).isEmpty();
    }

    @Test
    void executeSelectedDeactivatesMenu() {
        controller.onInputChanged("/");
        controller.executeSelected();
        assertThat(controller.toRenderEvent().visible()).isFalse();
    }

    @Test
    void hiddenCommandsNotShown() {
        var hiddenDef = new CommandDefinition(
            new CommandMetadata("secret", List.of(), "hidden", CommandKind.LOCAL, true), ctx -> {});
        var visibleDef = new CommandDefinition(
            new CommandMetadata("exit", List.of(), "退出", CommandKind.UI, false), ctx -> {});
        var registry = new CommandRegistry(List.of(hiddenDef, visibleDef));
        var ctrl = new CompletionMenuController(registry);
        ctrl.onInputChanged("/");
        assertThat(ctrl.toRenderEvent().entries()).hasSize(1);
        assertThat(ctrl.toRenderEvent().entries().get(0).name()).isEqualTo("exit");
    }
}
