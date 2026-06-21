package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import org.jline.utils.AttributedString;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTypesTest {

    // ==== DeltaEvent ====
    @Test
    void deltaContentShouldStoreText() {
        var e = new DeltaEvent.Content("hello");
        assertThat(e.text()).isEqualTo("hello");
    }

    @Test
    void deltaThinkingShouldStoreText() {
        var e = new DeltaEvent.Thinking("reasoning");
        assertThat(e.text()).isEqualTo("reasoning");
    }

    @Test
    void deltaCompleteShouldInstantiate() {
        assertThat(new DeltaEvent.Complete()).isNotNull();
    }

    @Test
    void deltaErrorShouldStoreMessageAndCode() {
        var e = new DeltaEvent.Error("timeout", 503);
        assertThat(e.message()).isEqualTo("timeout");
        assertThat(e.statusCode()).isEqualTo(503);
    }

    @Test
    void deltaUsageShouldStoreTokenCounts() {
        var e = new DeltaEvent.Usage(100, 50);
        assertThat(e.inputTokens()).isEqualTo(100);
        assertThat(e.outputTokens()).isEqualTo(50);
    }

    // ==== StyleCatalog ====
    @Test
    void styleCatalogShouldHave14Entries() {
        assertThat(StyleCatalog.values()).hasSize(14);
    }

    @Test
    void styleCatalogShouldContainCodeBlock() {
        assertThat(StyleCatalog.valueOf("CODE_BLOCK")).isNotNull();
    }

    // ==== RenderedLine ====
    @Test
    void renderedLineShouldStoreSegments() {
        var seg = new AttributedString("text");
        var line = new RenderedLine(seg);
        assertThat(line.segments()).hasSize(1);
    }

    // ==== Theme ====
    @Test
    void darkThemeShouldHaveAllStyleEntries() {
        Theme dark = Theme.dark();
        assertThat(dark.name()).isEqualTo("dark");
        for (StyleCatalog key : StyleCatalog.values()) {
            assertThat(dark.styles()).as("missing key: " + key).containsKey(key);
        }
    }

    @Test
    void themeApplyShouldReturnAttributedString() {
        Theme dark = Theme.dark();
        var result = dark.apply(StyleCatalog.PROMPT, "> ");
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("> ");
    }

    @Test
    void themeStylesShouldBeUnmodifiable() {
        Theme dark = Theme.dark();
        assertThatThrownBy(() -> dark.styles().put(StyleCatalog.PROMPT, null))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
