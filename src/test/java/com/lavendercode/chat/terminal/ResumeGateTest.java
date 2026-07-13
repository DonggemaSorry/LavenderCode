package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeGateTest {

    @Test
    void agentRunningTakesPriority() {
        assertThat(ResumeGate.check(true, true)).isEqualTo("请等待当前任务完成");
        assertThat(ResumeGate.check(true, false)).isEqualTo("请等待当前任务完成");
    }

    @Test
    void resumingBlocksResumeWhenAgentIdle() {
        assertThat(ResumeGate.check(false, true)).isEqualTo("恢复中");
    }

    @Test
    void returnsNullWhenResumeCanStart() {
        assertThat(ResumeGate.check(false, false)).isNull();
    }
}
