package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderEventTest {

    // ---- AppendToMessage ----
    @Test
    void appendToMessageShouldStoreText() {
        var e = new RenderEvent.AppendToMessage("delta");
        assertThat(e.text()).isEqualTo("delta");
    }

    // ---- FinalizeMessage ----
    @Test
    void finalizeMessageShouldInstantiate() {
        assertThat(new RenderEvent.FinalizeMessage()).isInstanceOf(RenderEvent.class);
    }

    // ---- AddUserMessage ----
    @Test
    void addUserMessageShouldStoreText() {
        var e = new RenderEvent.AddUserMessage("hello");
        assertThat(e.text()).isEqualTo("hello");
    }

    // ---- AddSystemMessage ----
    @Test
    void addSystemMessageShouldStoreText() {
        var e = new RenderEvent.AddSystemMessage("err");
        assertThat(e.text()).isEqualTo("err");
    }

    // ---- ThinkDelta ----
    @Test
    void thinkDeltaShouldStoreText() {
        var e = new RenderEvent.ThinkDelta("reasoning");
        assertThat(e.text()).isEqualTo("reasoning");
    }

    // ---- ScrollTo ----
    @Test
    void scrollToShouldRejectNegativeIndex() {
        assertThatThrownBy(() -> new RenderEvent.ScrollTo(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scrollToShouldAcceptZero() {
        var e = new RenderEvent.ScrollTo(0);
        assertThat(e.lineIndex()).isZero();
    }

    // ---- ScrollDelta ----
    @Test
    void scrollDeltaShouldStoreOffset() {
        var e = new RenderEvent.ScrollDelta(5);
        assertThat(e.offset()).isEqualTo(5);
    }

    // ---- ScrollAutoReset ----
    @Test
    void scrollAutoResetShouldInstantiate() {
        assertThat(new RenderEvent.ScrollAutoReset()).isInstanceOf(RenderEvent.class);
    }

    // ---- ClearChat ----
    @Test
    void clearChatShouldInstantiate() {
        assertThat(new RenderEvent.ClearChat()).isInstanceOf(RenderEvent.class);
    }

    // ---- WindowResize ----
    @Test
    void windowResizeShouldRejectInvalidDimensions() {
        assertThatThrownBy(() -> new RenderEvent.WindowResize(0, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void windowResizeShouldStoreDimensions() {
        var e = new RenderEvent.WindowResize(80, 24);
        assertThat(e.cols()).isEqualTo(80);
        assertThat(e.rows()).isEqualTo(24);
    }

    // ---- ThemeChange ----
    @Test
    void themeChangeShouldStoreTheme() {
        var theme = Theme.dark();
        var e = new RenderEvent.ThemeChange(theme);
        assertThat(e.theme()).isEqualTo(theme);
    }

    // ---- StatusUpdate ----
    @Test
    void statusUpdateShouldStoreFields() {
        var e = new RenderEvent.StatusUpdate("claude", 1234, true);
        assertThat(e.model()).isEqualTo("claude");
        assertThat(e.tokenCount()).isEqualTo(1234);
        assertThat(e.isEstimating()).isTrue();
    }

    // ---- RefreshInputChrome ----
    @Test
    void refreshInputChromeShouldInstantiate() {
        assertThat(new RenderEvent.RefreshInputChrome()).isInstanceOf(RenderEvent.class);
    }

    @Test
    void refreshInputChromeShouldAcceptLatch() {
        var latch = new java.util.concurrent.CountDownLatch(1);
        var e = new RenderEvent.RefreshInputChrome(latch);
        assertThat(e.done()).isSameAs(latch);
    }

    // ---- ScrollPageUp / ScrollPageDown ----
    @Test
    void scrollPageUpShouldInstantiate() {
        assertThat(new RenderEvent.ScrollPageUp()).isInstanceOf(RenderEvent.class);
    }

    @Test
    void scrollPageDownShouldInstantiate() {
        assertThat(new RenderEvent.ScrollPageDown()).isInstanceOf(RenderEvent.class);
    }

    // ---- UpdateInputDraft ----
    @Test
    void updateInputDraftShouldStoreFields() {
        var e = new RenderEvent.UpdateInputDraft("hello", 2);
        assertThat(e.draft()).isEqualTo("hello");
        assertThat(e.cursorIndex()).isEqualTo(2);
    }

    @Test
    void updateInputDraftShouldRejectInvalidCursor() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RenderEvent.UpdateInputDraft("hi", 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- RefreshAll ----
    @Test
    void refreshAllShouldInstantiate() {
        assertThat(new RenderEvent.RefreshAll()).isInstanceOf(RenderEvent.class);
    }

    // ---- Shutdown ----
    @Test
    void shutdownShouldInstantiate() {
        assertThat(new RenderEvent.Shutdown()).isInstanceOf(RenderEvent.class);
    }

    // ---- Sealed hierarchy ----
    @Test
    void shouldBeSealedInterface() {
        assertThat(new RenderEvent.AppendToMessage("x")).isInstanceOf(RenderEvent.class);
        assertThat(new RenderEvent.FinalizeMessage()).isInstanceOf(RenderEvent.class);
        assertThat(new RenderEvent.ScrollDelta(1)).isInstanceOf(RenderEvent.class);
        assertThat(new RenderEvent.Shutdown()).isInstanceOf(RenderEvent.class);
    }

    @Test
    void appendToMessageShouldRejectNull() {
        assertThatThrownBy(() -> new RenderEvent.AppendToMessage(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addUserMessageShouldRejectNull() {
        assertThatThrownBy(() -> new RenderEvent.AddUserMessage(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addSystemMessageShouldRejectNull() {
        assertThatThrownBy(() -> new RenderEvent.AddSystemMessage(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void thinkDeltaShouldRejectNull() {
        assertThatThrownBy(() -> new RenderEvent.ThinkDelta(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void themeChangeShouldRejectNull() {
        assertThatThrownBy(() -> new RenderEvent.ThemeChange(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void windowResizeShouldRejectZeroRows() {
        assertThatThrownBy(() -> new RenderEvent.WindowResize(80, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
