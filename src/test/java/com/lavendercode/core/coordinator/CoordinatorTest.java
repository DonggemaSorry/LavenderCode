package com.lavendercode.core.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lavendercode.core.config.Options;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoordinatorTest {
    @Test
    void requiresBothLocks() {
        Options off = new Options().withCoordinatorMode(false);
        assertThat(Coordinator.isEnabled(off, k -> "1")).isFalse();
        Options on = new Options().withCoordinatorMode(true);
        assertThat(Coordinator.isEnabled(on, k -> null)).isFalse();
        assertThat(Coordinator.isEnabled(on, k -> "1")).isTrue();
        assertThat(Coordinator.isEnabled(on, k -> "yes")).isTrue();
    }

    @Test
    void allowedToolsExcludesWriteEdit() {
        assertThat(Coordinator.ALLOWED_TOOLS).contains("execute_command", "TeamSendMessage");
        assertThat(Coordinator.ALLOWED_TOOLS).doesNotContain("write_file", "edit_file");
    }
}
