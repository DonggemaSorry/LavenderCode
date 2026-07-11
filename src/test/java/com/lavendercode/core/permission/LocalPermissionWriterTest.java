package com.lavendercode.core.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPermissionWriterTest {
    @Test
    void appendExactAllowRule(@TempDir Path root) throws Exception {
        Path local = root.resolve(".lavendercode/permissions.local.yaml");
        LocalPermissionWriter.appendRule(local, "Write(src/Foo.java)", PermissionRule.Effect.ALLOW);
        String content = Files.readString(local);
        assertThat(content).contains("Write(src/Foo.java)");
        assertThat(content).contains("allow");
    }
}
