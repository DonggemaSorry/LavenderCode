package com.lavendercode.core.context;

public final class SummaryPromptBuilder {
    private SummaryPromptBuilder() {}

    public static String compactionInstruction() {
        return """
            You are compressing a long coding-agent conversation into a structured summary.
            Rules:
            - Do NOT call any tools.
            - First write your analysis inside <analysis>...</analysis> (this will be discarded).
            - Then write the formal summary inside <summary>...</summary>.
            - The summary MUST contain these 9 sections in order:
              1. Primary requests and intent
              2. Key technical concepts
              3. Files and code sections
              4. Errors and fixes
              5. Problem-solving process
              6. All user messages (preserve original user message text verbatim when possible)
              7. Pending tasks
              8. Current work (most detailed: what is being done, where it stopped)
              9. Possible next steps
            """;
    }
}
