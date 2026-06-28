package com.lavendercode.core.tool;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {
    @Test
    void successResult() {
        var r = ToolResult.success("ok", "content");
        assertThat(r.success()).isTrue();
        assertThat(r.summary()).isEqualTo("ok");
        assertThat(r.content()).isEqualTo("content");
        assertThat(r.errorCategory()).isNull();
    }

    @Test
    void successWithTruncation() {
        var r = ToolResult.success("ok", "c", new TruncationInfo(5, 2, 0, 2));
        assertThat(r.truncationInfo()).isNotNull();
        assertThat(r.truncationInfo().totalCount()).isEqualTo(5);
    }

    @Test
    void errorResult() {
        var r = ToolResult.error("FILE_NOT_FOUND", "文件不存在", "detail");
        assertThat(r.success()).isFalse();
        assertThat(r.errorCategory()).isEqualTo("FILE_NOT_FOUND");
        assertThat(r.summary()).isEqualTo("文件不存在");
        assertThat(r.errorDetail()).isEqualTo("detail");
    }

    @Test
    void timeoutError() {
        var r = ToolResult.error("TIMEOUT", "超时", "over 120s");
        assertThat(r.errorCategory()).isEqualTo("TIMEOUT");
        assertThat(r.success()).isFalse();
    }
}
