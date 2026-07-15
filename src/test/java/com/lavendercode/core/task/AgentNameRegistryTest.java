package com.lavendercode.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentNameRegistryTest {
    @Test
    void laterRegisterOverrides() {
        AgentNameRegistry r = new AgentNameRegistry();
        r.register("alice", "id-1");
        r.register("alice", "id-2");
        assertThat(r.resolve("alice")).contains("id-2");
        assertThat(r.nameOf("id-2")).contains("alice");
        assertThat(r.resolve("id-2")).contains("id-2");
    }
}
