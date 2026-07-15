package com.lavendercode.core.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedTaskStoreTest {
    @TempDir Path dir;

    @Test
    void createAndAddBlockedByUpdatesBidirectional() {
        SharedTaskStore store = new SharedTaskStore(dir);
        String a = store.create("A", "", null, List.of());
        String b = store.create("B", "", null, List.of());
        store.update(b, null, null, null, null, List.of(), List.of(a), List.of(), List.of());
        SharedTask taskB = store.get(b).orElseThrow();
        SharedTask taskA = store.get(a).orElseThrow();
        assertThat(taskB.blockedBy()).contains(a);
        assertThat(taskA.blocks()).contains(b);
    }

    @Test
    void isReadyFalseWhenBlockerPending() {
        SharedTaskStore store = new SharedTaskStore(dir);
        String a = store.create("A", "", null, List.of());
        String b = store.create("B", "", null, List.of(a));
        assertThat(store.list("pending").stream().filter(t -> t.id().equals(b)).findFirst().orElseThrow().isReady())
            .isFalse();
        store.update(a, null, null, "completed", null, List.of(), List.of(), List.of(), List.of());
        assertThat(store.list(null).stream().filter(t -> t.id().equals(b)).findFirst().orElseThrow().isReady())
            .isTrue();
    }
}
