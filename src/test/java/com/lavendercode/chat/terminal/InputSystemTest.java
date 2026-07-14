package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InputSystemTest {

    @Test
    void executeCommandRecordHoldsRawInput() {
        var event = new InputEvent.ExecuteCommand("/exit");
        assertThat(event.rawInput()).isEqualTo("/exit");
    }

    @Test
    void cancelAgentRecordExists() {
        var event = new InputEvent.CancelAgent();
        assertThat(event).isInstanceOf(InputEvent.class);
    }

    @Test
    void scrollEventRecordHoldsCommand() {
        var event = new InputEvent.ScrollEvent("up");
        assertThat(event.command()).isEqualTo("up");
    }
}
