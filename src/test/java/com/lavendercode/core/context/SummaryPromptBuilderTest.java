package com.lavendercode.core.context;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SummaryPromptBuilderTest {
    @Test
    void includesNineSectionsToolBanAndXmlTags() {
        String prompt = SummaryPromptBuilder.compactionInstruction();

        assertThat(prompt).contains("1. Primary requests and intent");
        assertThat(prompt).contains("9. Possible next steps");
        assertThat(prompt).contains("Do NOT call any tools");
        assertThat(prompt).contains("<analysis>");
        assertThat(prompt).contains("</analysis>");
        assertThat(prompt).contains("<summary>");
        assertThat(prompt).contains("</summary>");
    }
}
