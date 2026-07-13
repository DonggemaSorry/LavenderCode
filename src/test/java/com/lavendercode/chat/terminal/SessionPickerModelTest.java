package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionListItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPickerModelTest {

    @Test
    void filterMatchesTitleModelAndSessionId() {
        SessionPickerModel model = new SessionPickerModel(List.of(
            item("20260713-101500-a1b2c3d4", "Fix checkout flow", "gpt-4"),
            item("20260712-091200-e5f6a7b8", "Write release notes", "claude")
        ));

        model.setFilter("checkout");
        assertThat(model.visibleItems()).extracting(SessionListItem::title)
            .containsExactly("Fix checkout flow");

        model.setFilter("CLAUDE");
        assertThat(model.visibleItems()).extracting(SessionListItem::model)
            .containsExactly("claude");

        model.setFilter("a1b2");
        assertThat(model.visibleItems()).extracting(SessionListItem::sessionId)
            .containsExactly("20260713-101500-a1b2c3d4");
    }

    @Test
    void moveSelectionWrapsWithinVisibleItems() {
        SessionPickerModel model = new SessionPickerModel(List.of(
            item("20260713-101500-a1b2c3d4", "First", "gpt-4"),
            item("20260712-091200-e5f6a7b8", "Second", "gpt-4"),
            item("20260711-080000-c9d0e1f2", "Third", "gpt-4")
        ));

        assertThat(model.selectedIndex()).isZero();
        model.moveDown();
        assertThat(model.selectedItem().title()).isEqualTo("Second");
        model.moveUp();
        assertThat(model.selectedItem().title()).isEqualTo("First");
        model.moveUp();
        assertThat(model.selectedItem().title()).isEqualTo("Third");
        model.moveDown();
        assertThat(model.selectedItem().title()).isEqualTo("First");
    }

    @Test
    void selectionClampsWhenFilterShrinksList() {
        SessionPickerModel model = new SessionPickerModel(List.of(
            item("20260713-101500-a1b2c3d4", "Alpha", "gpt-4"),
            item("20260712-091200-e5f6a7b8", "Beta", "gpt-4")
        ));
        model.moveDown();

        model.setFilter("Alpha");

        assertThat(model.selectedIndex()).isZero();
        assertThat(model.selectedItem().title()).isEqualTo("Alpha");
    }

    @Test
    void emptyFilterResultHasNoSelection() {
        SessionPickerModel model = new SessionPickerModel(List.of(
            item("20260713-101500-a1b2c3d4", "Alpha", "gpt-4")
        ));

        model.setFilter("missing");

        assertThat(model.visibleItems()).isEmpty();
        assertThat(model.selectedIndex()).isEqualTo(-1);
        assertThat(model.selectedItem()).isNull();
    }

    private static SessionListItem item(String id, String title, String model) {
        return new SessionListItem(id, title, "刚刚", model, 128, Path.of(id, "conversation.jsonl"));
    }
}
