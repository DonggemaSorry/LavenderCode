package com.lavendercode.core.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentInfoCollectorTest {
    @Test
    void containsWorkingDirectory() {
        String result = EnvironmentInfoCollector.collect("test-model", "1.0.0");
        assertThat(result).contains("Working directory:");
        assertThat(result).contains(System.getProperty("user.dir").replace("\\", "/"));
    }

    @Test
    void containsPlatform() {
        String result = EnvironmentInfoCollector.collect("test-model", "1.0.0");
        assertThat(result).contains("Platform:");
        assertThat(result).contains(System.getProperty("os.name"));
    }

    @Test
    void containsDate() {
        String result = EnvironmentInfoCollector.collect("test-model", "1.0.0");
        assertThat(result).contains("Date:");
        assertThat(result).contains(java.time.LocalDate.now().toString());
    }

    @Test
    void containsModelAndVersion() {
        String result = EnvironmentInfoCollector.collect("claude-sonnet-4", "2.1.0");
        assertThat(result).contains("claude-sonnet-4");
        assertThat(result).contains("2.1.0");
    }

    @Test
    void includesGitStatusInGitRepo() {
        String result = EnvironmentInfoCollector.collect("m", "v");
        // This test runs in the project git repo, so git status should be present
        assertThat(result).contains("Git status:");
    }

    @Test
    void doesNotContainApiKey() {
        String result = EnvironmentInfoCollector.collect("m", "v");
        assertThat(result).doesNotContain("api_key");
        assertThat(result).doesNotContain("apiKey");
        assertThat(result).doesNotContain("sk-");
    }
}
