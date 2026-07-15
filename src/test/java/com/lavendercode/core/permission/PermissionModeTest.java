package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PermissionModeTest {

    @Test
    void fromYamlAcceptsDontAsk() {
        assertThat(PermissionMode.fromYaml("dontAsk")).isEqualTo(PermissionMode.DONT_ASK);
    }
}
