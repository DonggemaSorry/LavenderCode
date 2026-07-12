package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SummaryResponseParserTest {
    @Test
    void extractsSummaryDiscardsAnalysis() {
        String raw = "<analysis>draft</analysis><summary>## 1. Intent\ncontent</summary>";
        assertThat(SummaryResponseParser.extractSummary(raw)).contains("## 1. Intent");
        assertThat(SummaryResponseParser.extractSummary(raw)).doesNotContain("draft");
    }

    @Test
    void returnsTrimmedRawWhenNoSummaryTag() {
        String raw = "plain summary text";
        assertThat(SummaryResponseParser.extractSummary(raw)).isEqualTo("plain summary text");
    }

    @Test
    void handlesNullInput() {
        assertThat(SummaryResponseParser.extractSummary(null)).isEmpty();
    }
}
