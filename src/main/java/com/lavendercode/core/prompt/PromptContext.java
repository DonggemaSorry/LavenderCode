package com.lavendercode.core.prompt;

import java.util.List;

public record PromptContext(
    String stablePrompt,
    String environmentInfo,
    List<String> reminders
) {}
