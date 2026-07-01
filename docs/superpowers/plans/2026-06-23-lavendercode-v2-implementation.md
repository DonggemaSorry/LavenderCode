# LavenderCode v2 Full Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all 12 functional requirements (F1–F12) and 7 non-functional requirements (N1–N7) from the v2 PRD, upgrading the single-provider terminal chat application to multi-provider with markdown rendering, response timing, and refined terminal UX.

**Architecture:** Incremental refactoring of the existing 4-thread architecture (lavender-input, lavender-network, lavender-render, lavender-timer). Config layer restructured from single `provider` to `providers` list. New components: ProviderSelector, MarkdownRenderer, ResponseTimer. Thinking deltas discarded at the protocol boundary. Status bar expanded to 3-column layout with response timer.

**Tech Stack:** Java 21, JLine 3.26.3, OkHttp 4.12.0, Jackson 2.17.2, Jakarta Validation 3.0.2, JUnit 5.10.3, Mockito 5.12.0, MockWebServer 4.12.0, AssertJ 3.26.3. New dependency: flexmark-java (com.vladsch.flexmark).

---

### Task 1: Add flexmark-java to pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add flexmark-java dependency**

```xml
<!-- Inside <dependencies> block, after the last existing dependency -->
<!-- flexmark-java for markdown rendering -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

- [ ] **Step 2: Verify dependency resolves**

Run: `mvn dependency:resolve -q`
Expected: BUILD SUCCESS, flexmark appears in resolved dependencies.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add flexmark-java 0.64.8 for markdown rendering"
```

---

### Task 2: Restructure Config Layer (ProviderConfig + ThinkingConfig + Options + LlmConfig)

**Files:**
- Modify: `src/main/java/com/lavendercode/core/config/ProviderConfig.java`
- Modify: `src/main/java/com/lavendercode/core/config/Options.java`
- Modify: `src/main/java/com/lavendercode/core/config/LlmConfig.java`
- Modify: `src/test/resources/config-valid-anthropic.yaml`
- Modify: `src/test/resources/config-valid-openai.yaml`
- Modify: `src/test/resources/config-missing-api-key.yaml`
- Modify: `src/test/resources/config-invalid-yaml.yaml`
- Create: `src/test/resources/config-valid-multi-provider.yaml`
- Create: `src/test/resources/config-empty-providers.yaml`
- Modify: `src/test/java/com/lavendercode/core/config/ConfigLoaderTest.java`

- [ ] **Step 1: Write failing test — update ConfigLoaderTest for providers list**

Replace the entire `ConfigLoaderTest.java` content:

```java
package com.lavendercode.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadSingleProviderConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-anthropic.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        List<ProviderConfig> providers = config.providers();
        assertThat(providers).hasSize(1);
        assertThat(providers.get(0).protocol()).isEqualTo("anthropic");
        assertThat(providers.get(0).model()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(providers.get(0).baseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(providers.get(0).apiKey()).isEqualTo("sk-ant-test-key");
        assertThat(providers.get(0).name()).isNull();
        assertThat(providers.get(0).thinking().enabled()).isTrue();
        assertThat(providers.get(0).thinking().budgetTokens()).isEqualTo(4000);
        assertThat(config.options().maxTokens()).isEqualTo(4096);
        assertThat(config.options().systemPrompt()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void shouldLoadMultiProviderConfig() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-multi-provider.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers()).hasSize(2);
        assertThat(config.providers().get(0).name()).isEqualTo("DeepSeek");
        assertThat(config.providers().get(0).protocol()).isEqualTo("openai");
        assertThat(config.providers().get(1).name()).isEqualTo("Claude");
        assertThat(config.providers().get(1).protocol()).isEqualTo("anthropic");
    }

    @Test
    void shouldAcceptNullBaseUrl() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).baseUrl()).isNull();
    }

    @Test
    void shouldThrowWhenMissingApiKey() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-missing-api-key.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("apiKey");
    }

    @Test
    void shouldThrowWhenEmptyProviders() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-empty-providers.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenInvalidYaml() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-invalid-yaml.yaml"),
            configFile
        );

        assertThatThrownBy(() -> ConfigLoader.load(configFile))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("YAML");
    }

    @Test
    void shouldThrowWhenFileNotFound() {
        Path nonExistent = tempDir.resolve("nonexistent.yaml");
        assertThatThrownBy(() -> ConfigLoader.load(nonExistent))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldUseDefaultOptionsWhenNotProvided() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        Files.copy(
            getClass().getResourceAsStream("/config-valid-openai.yaml"),
            configFile
        );

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.options().systemPrompt()).isEmpty();
        assertThat(config.options().maxTokens()).isEqualTo(4096);
    }

    @Test
    void shouldLoadConfigWithOnlyRequiredFields() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).protocol()).isEqualTo("openai");
        assertThat(config.providers().get(0).apiKey()).isEqualTo("sk-test");
        assertThat(config.options().maxTokens()).isEqualTo(4096);
        assertThat(config.options().systemPrompt()).isEmpty();
    }

    @Test
    void shouldHandleSystemPromptWithSpecialCharacters() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: anthropic
                model: claude-sonnet-4-20250514
                api_key: sk-ant-test
            options:
              system_prompt: "You are helpful. Use 你好 for greetings."
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.options().systemPrompt())
            .isEqualTo("You are helpful. Use 你好 for greetings.");
    }

    @Test
    void shouldUseDefaultThinkingWhenNotProvided() throws Exception {
        Path configFile = tempDir.resolve("config.yaml");
        String yaml = """
            providers:
              - protocol: openai
                model: gpt-4o
                api_key: sk-test
            """;
        Files.writeString(configFile, yaml);

        LlmConfig config = ConfigLoader.load(configFile);

        assertThat(config.providers().get(0).thinking().enabled()).isFalse();
        assertThat(config.providers().get(0).thinking().budgetTokens()).isEqualTo(1024);
    }
}
```

- [ ] **Step 2: Update test YAML files to use providers list format**

Update `src/test/resources/config-valid-anthropic.yaml`:

```yaml
providers:
  - protocol: anthropic
    model: claude-sonnet-4-20250514
    base_url: https://api.anthropic.com
    api_key: sk-ant-test-key
    thinking:
      enabled: true
      budget_tokens: 4000

options:
  max_tokens: 4096
  system_prompt: "You are a helpful assistant."
```

Update `src/test/resources/config-valid-openai.yaml`:

```yaml
providers:
  - protocol: openai
    model: gpt-4o
    base_url: https://api.openai.com
    api_key: sk-test-key

options:
  max_tokens: 2048
```

Update `src/test/resources/config-missing-api-key.yaml`:

```yaml
providers:
  - protocol: openai
    model: gpt-4o
    base_url: https://api.openai.com
```

Update `src/test/resources/config-invalid-yaml.yaml`:

```yaml
providers:
  - protocol: openai
    model: gpt-4o
      base_url: https://api.openai.com  # bad indent
    api_key: sk-test-key
```

Create `src/test/resources/config-valid-multi-provider.yaml`:

```yaml
providers:
  - name: DeepSeek
    protocol: openai
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: sk-test-key1
  - name: Claude
    protocol: anthropic
    model: claude-sonnet-4-20250514
    base_url: https://api.anthropic.com
    api_key: sk-ant-test2

options:
  max_tokens: 4096
  system_prompt: "You are a helpful AI assistant."
```

Create `src/test/resources/config-empty-providers.yaml`:

```yaml
providers: []
```

- [ ] **Step 3: Run tests to verify failure**

Run: `mvn test -pl . -Dtest=ConfigLoaderTest -q`
Expected: Compilation FAIL — `config.provider()` no longer exists, `LlmConfig` expects `providers`.

- [ ] **Step 4: Rewrite ProviderConfig.java**

Replace the entire file:

```java
package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProviderConfig(
    @JsonProperty("name")
    String name,

    @JsonProperty("protocol")
    @NotNull
    String protocol,

    @JsonProperty("model")
    @NotNull
    String model,

    @JsonProperty("base_url")
    String baseUrl,

    @JsonProperty("api_key")
    @NotNull
    String apiKey,

    @JsonProperty("thinking")
    ThinkingConfig thinking
) {
    public ProviderConfig {
        if (thinking == null) {
            thinking = new ThinkingConfig();
        }
    }
}
```

- [ ] **Step 5: Rewrite Options.java**

Replace the entire file:

```java
package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Options(
    @JsonProperty("max_tokens")
    int maxTokens,

    @JsonProperty("system_prompt")
    String systemPrompt
) {
    public Options() {
        this(4096, "");
    }

    public Options {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
    }
}
```

- [ ] **Step 6: Rewrite LlmConfig.java**

Replace the entire file:

```java
package com.lavendercode.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;

public record LlmConfig(
    @JsonProperty("providers")
    @NotNull
    @Valid
    List<ProviderConfig> providers,

    @JsonProperty("options")
    Options options
) {
    public LlmConfig {
        if (options == null) {
            options = new Options();
        }
    }

    /** Returns an unmodifiable view of the providers list. */
    public List<ProviderConfig> providers() {
        return Collections.unmodifiableList(providers);
    }
}
```

- [ ] **Step 7: Update ConfigLoader.java for providers list validation**

Replace the entire file:

```java
package com.lavendercode.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigLoader {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    public static LlmConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            throw new ConfigException(
                "Config file not found: " + configPath,
                "file"
            );
        }

        LlmConfig config;
        try {
            config = mapper.readValue(configPath.toFile(), LlmConfig.class);
        } catch (IOException e) {
            throw new ConfigException(
                "Failed to parse YAML config: " + e.getMessage(),
                "format"
            );
        }

        if (config.providers().isEmpty()) {
            throw new ConfigException(
                "Config error: providers list is empty",
                "providers"
            );
        }

        for (int i = 0; i < config.providers().size(); i++) {
            ProviderConfig pc = config.providers().get(i);
            Set<ConstraintViolation<ProviderConfig>> violations = validator.validate(pc);
            if (!violations.isEmpty()) {
                String fields = violations.stream()
                    .map(v -> "providers[" + i + "]." + v.getPropertyPath().toString())
                    .collect(Collectors.joining(", "));
                throw new ConfigException(
                    "Config error: missing required fields: " + fields,
                    fields
                );
            }
        }

        return config;
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=ConfigLoaderTest -q`
Expected: All 10 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/lavendercode/core/config/ProviderConfig.java \
        src/main/java/com/lavendercode/core/config/Options.java \
        src/main/java/com/lavendercode/core/config/LlmConfig.java \
        src/main/java/com/lavendercode/core/config/ConfigLoader.java \
        src/test/java/com/lavendercode/core/config/ConfigLoaderTest.java \
        src/test/resources/config-valid-anthropic.yaml \
        src/test/resources/config-valid-openai.yaml \
        src/test/resources/config-missing-api-key.yaml \
        src/test/resources/config-invalid-yaml.yaml \
        src/test/resources/config-valid-multi-provider.yaml \
        src/test/resources/config-empty-providers.yaml
git commit -m "refactor: restructure config to providers list with per-provider thinking"
```

---

### Task 3: Remove Thinking from DeltaEvent + RenderEvent

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/DeltaEvent.java`
- Modify: `src/main/java/com/lavendercode/chat/terminal/RenderEvent.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/DataTypesTest.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/RenderEventTest.java`

- [ ] **Step 1: Write failing test — DataTypesTest should not reference Thinking**

Update `DataTypesTest.java` to not import or reference `DeltaEvent.Thinking`. Read the current file first, then modify.

- [ ] **Step 2: Remove Thinking from DeltaEvent.java**

Replace the entire file:

```java
package com.lavendercode.chat.terminal;

import java.util.Objects;

public sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {

    record Content(String text) implements DeltaEvent {
        public Content { Objects.requireNonNull(text); }
    }

    record Complete() implements DeltaEvent {}

    record Error(String message, int statusCode) implements DeltaEvent {
        public Error { Objects.requireNonNull(message); }
    }

    record Usage(int inputTokens, int outputTokens) implements DeltaEvent {}
}
```

- [ ] **Step 3: Expand StatusUpdate and remove ThinkDelta from RenderEvent.java**

Replace the entire file:

```java
package com.lavendercode.chat.terminal;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public sealed interface RenderEvent
    permits RenderEvent.AppendToMessage,
            RenderEvent.FinalizeMessage,
            RenderEvent.AddUserMessage,
            RenderEvent.AddSystemMessage,
            RenderEvent.ScrollTo,
            RenderEvent.ScrollDelta,
            RenderEvent.ScrollPageUp,
            RenderEvent.ScrollPageDown,
            RenderEvent.ScrollAutoReset,
            RenderEvent.ClearChat,
            RenderEvent.WindowResize,
            RenderEvent.StatusUpdate,
            RenderEvent.RefreshInputChrome,
            RenderEvent.UpdateInputDraft,
            RenderEvent.RefreshAll,
            RenderEvent.Shutdown {

    record AppendToMessage(String text) implements RenderEvent {
        public AppendToMessage { Objects.requireNonNull(text); }
    }

    record FinalizeMessage() implements RenderEvent {}

    record AddUserMessage(String text) implements RenderEvent {
        public AddUserMessage { Objects.requireNonNull(text); }
    }

    record AddSystemMessage(String text) implements RenderEvent {
        public AddSystemMessage { Objects.requireNonNull(text); }
    }

    record ScrollTo(int lineIndex) implements RenderEvent {
        public ScrollTo {
            if (lineIndex < 0) throw new IllegalArgumentException("lineIndex must be >= 0");
        }
    }

    record ScrollDelta(int offset) implements RenderEvent {}

    record ScrollPageUp() implements RenderEvent {}

    record ScrollPageDown() implements RenderEvent {}

    record ScrollAutoReset() implements RenderEvent {}

    record ClearChat() implements RenderEvent {}

    record WindowResize(int cols, int rows) implements RenderEvent {
        public WindowResize {
            if (cols < 1) throw new IllegalArgumentException("cols must be >= 1");
            if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
        }
    }

    record StatusUpdate(
        String providerName,
        String modelName,
        String statusText,
        int tokenCount
    ) implements RenderEvent {
        public StatusUpdate {
            Objects.requireNonNull(providerName, "providerName must not be null");
            Objects.requireNonNull(modelName, "modelName must not be null");
        }
    }

    record RefreshInputChrome(CountDownLatch done) implements RenderEvent {
        public RefreshInputChrome() { this(null); }
    }

    record UpdateInputDraft(String draft, int cursorIndex, CountDownLatch done) implements RenderEvent {
        public UpdateInputDraft(String draft, int cursorIndex) {
            this(draft != null ? draft : "", cursorIndex, null);
        }

        public UpdateInputDraft {
            Objects.requireNonNull(draft);
            if (cursorIndex < 0 || cursorIndex > draft.length()) {
                throw new IllegalArgumentException("cursorIndex out of range");
            }
        }
    }

    record RefreshAll() implements RenderEvent {}

    record Shutdown() implements RenderEvent {}
}
```

- [ ] **Step 4: Run compilation check**

Run: `mvn compile -q`
Expected: Compilation errors in files that still reference `DeltaEvent.Thinking`, `RenderEvent.ThinkDelta`, or old `StatusUpdate` constructor. These will be fixed in subsequent tasks. Let the errors guide us.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/DeltaEvent.java \
        src/main/java/com/lavendercode/chat/terminal/RenderEvent.java
git commit -m "refactor: remove Thinking from DeltaEvent/RenderEvent, expand StatusUpdate"
```

---

### Task 4: Update DeltaBuffer — Remove THINK_DELTA

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/DeltaBuffer.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/DeltaBufferTest.java`

- [ ] **Step 1: Update DeltaBuffer.java — remove THINK_DELTA**

Replace lines 16-22 (BufferedEvent.Type enum):

```java
        public enum Type {
            CONTENT_DELTA,
            STREAM_COMPLETE, STREAM_ERROR,
            USER_MESSAGE, SYSTEM_MESSAGE
        }
```

Replace lines 117-151 (buildBatch + flushBuffer methods):

```java
    private List<RenderEvent> buildBatch(List<BufferedEvent> snapshot) {
        List<RenderEvent> result = new ArrayList<>();
        StringBuilder textBuf = new StringBuilder();
        BufferedEvent.Type lastType = null;

        for (BufferedEvent e : snapshot) {
            if (e.type != lastType && lastType != null) {
                flushBuffer(result, lastType, textBuf);
            }
            switch (e.type) {
                case CONTENT_DELTA -> textBuf.append(e.text);
                case STREAM_COMPLETE, STREAM_ERROR, USER_MESSAGE, SYSTEM_MESSAGE -> {
                    flushBuffer(result, lastType, textBuf);
                    result.add(e.toRenderEvent());
                }
            }
            lastType = e.type;
        }
        flushBuffer(result, lastType, textBuf);
        return result;
    }

    private void flushBuffer(List<RenderEvent> result, BufferedEvent.Type type,
                             StringBuilder textBuf) {
        if (type == BufferedEvent.Type.CONTENT_DELTA && textBuf.length() > 0) {
            result.add(new RenderEvent.AppendToMessage(textBuf.toString()));
            textBuf.setLength(0);
        }
    }
```

Remove the `thinkBuf` StringBuilder variable from `buildBatch` (line 120).

- [ ] **Step 2: Update DeltaBufferTest.java**

Remove test method `contentAndThinkShouldBeOrdered` and any test referencing `THINK_DELTA`. Replace `singleDeltaShouldFlushViaTimer` and `adjacentSameTypeShouldMerge` with versions that do not reference thinkBuf. Read the file to identify exact lines to change.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl . -Dtest=DeltaBufferTest -q`
Expected: All remaining tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/DeltaBuffer.java \
        src/test/java/com/lavendercode/chat/terminal/DeltaBufferTest.java
git commit -m "refactor: remove THINK_DELTA type from DeltaBuffer"
```

---

### Task 5: Update StreamingChatService — Discard Thinking Delta

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/StreamingChatService.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/StreamingChatServiceTest.java`

- [ ] **Step 1: Write failing test in StreamingChatServiceTest**

Add a new test method to verify that `ThinkingDelta` is not converted to a `DeltaEvent`:

```java
@Test
void shouldSkipThinkingDelta() throws Exception {
    // Create a provider that emits ThinkingDelta
    LlmProvider mockProvider = mock(LlmProvider.class);
    StreamEventIterator iterator = mock(StreamEventIterator.class);
    when(iterator.hasNext()).thenReturn(true, true, false);
    when(iterator.next()).thenReturn(
        new StreamEvent.ThinkingDelta("thinking..."),
        new StreamEvent.ContentDelta("hello")
    );
    when(mockProvider.streamChat(any(), any())).thenReturn(iterator);

    List<DeltaEvent> events = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    ChatService service = new StreamingChatService();
    service.submit(mockProvider, List.of(), new LlmConfig(
        List.of(new ProviderConfig(null, "openai", "test", null, "sk-test", null)),
        new Options()
    ), delta -> {
        events.add(delta);
        if (delta instanceof DeltaEvent.Complete) latch.countDown();
    });

    latch.await(2, TimeUnit.SECONDS);

    // Should only see Content, not Thinking
    assertThat(events).noneMatch(d -> d instanceof DeltaEvent.Thinking);
    assertThat(events).anyMatch(d -> d instanceof DeltaEvent.Content);
}
```

Note: This test requires imports of `com.lavendercode.core.provider.StreamEvent` and the config classes.

- [ ] **Step 2: Update StreamingChatService.toDeltaEvent()**

Replace line 78 in `toDeltaEvent`:

```java
    private DeltaEvent toDeltaEvent(StreamEvent se) {
        return switch (se) {
            case StreamEvent.ContentDelta cd  -> new DeltaEvent.Content(cd.text());
            case StreamEvent.ThinkingDelta td -> null;  // discard thinking
            case StreamEvent.StreamComplete sc -> null;
            case StreamEvent.StreamError err  -> new DeltaEvent.Error(err.message(), err.statusCode());
        };
    }
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl . -Dtest=StreamingChatServiceTest -q`
Expected: All tests PASS including the new `shouldSkipThinkingDelta`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/StreamingChatService.java \
        src/test/java/com/lavendercode/chat/terminal/StreamingChatServiceTest.java
git commit -m "feat: discard ThinkingDelta in StreamingChatService (F5)"
```

---

### Task 6: Update Providers — thinking from ProviderConfig

**Files:**
- Modify: `src/main/java/com/lavendercode/core/anthropic/AnthropicProvider.java`
- Modify: `src/main/java/com/lavendercode/core/openai/OpenAIProvider.java`
- Modify: `src/test/java/com/lavendercode/core/anthropic/AnthropicProviderTest.java`
- Modify: `src/test/java/com/lavendercode/core/openai/OpenAIProviderTest.java`

- [ ] **Step 1: Update AnthropicProvider.java — thinking from config.provider()**

In `buildRequestBody`, lines 100-105, change `config.options().thinking()` to `config.provider().thinking()`:

```java
        if (config.provider().thinking() != null && config.provider().thinking().enabled()) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", config.provider().thinking().budgetTokens());
            body.put("thinking", thinking);
        }
```

In `streamChat`, line 42, where `config.provider().baseUrl()` is called — this now needs to handle null:

```java
    @Override
    public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
        String baseUrl = config.provider().baseUrl();
        if (baseUrl == null) {
            baseUrl = "https://api.anthropic.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        // ... rest unchanged
```

- [ ] **Step 2: Update OpenAIProvider.java — null-safe baseUrl, silently ignore thinking**

In `streamChat`, line 42 area, add null handling for baseUrl:

```java
    @Override
    public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
        String baseUrl = config.provider().baseUrl();
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com";
        }
        // ... rest unchanged
```

In `buildRequestBody`, the method does not reference thinking currently — no change needed (thinking is silently ignored).

- [ ] **Step 3: Update AnthropicProviderTest and OpenAIProviderTest**

Update constructor calls from `new LlmConfig(new ProviderConfig(...), new Options(...))` to `new LlmConfig(List.of(new ProviderConfig(...)), new Options(...))`.

Fix any test using `config.provider()` single access — these must now be `config.providers().get(0)`.

- [ ] **Step 4: Run provider tests**

Run: `mvn test -pl . -Dtest="AnthropicProviderTest,OpenAIProviderTest,AnthropicIntegrationTest,OpenAIIntegrationTest" -q`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/core/anthropic/AnthropicProvider.java \
        src/main/java/com/lavendercode/core/openai/OpenAIProvider.java \
        src/test/java/com/lavendercode/core/anthropic/AnthropicProviderTest.java \
        src/test/java/com/lavendercode/core/openai/OpenAIProviderTest.java
git commit -m "refactor: providers read thinking and baseUrl from ProviderConfig"
```

---

### Task 7: Create ResponseTimer

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/ResponseTimer.java`
- Create: `src/test/java/com/lavendercode/chat/terminal/ResponseTimerTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/lavendercode/chat/terminal/ResponseTimerTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTimerTest {

    @Test
    void shouldReturnZeroBeforeStart() {
        ResponseTimer timer = new ResponseTimer();
        assertThat(timer.elapsedSeconds()).isEqualTo(0);
    }

    @Test
    void shouldReturnElapsedSeconds() throws InterruptedException {
        ResponseTimer timer = new ResponseTimer();
        timer.start();
        Thread.sleep(1100);  // wait > 1 second
        long elapsed = timer.elapsedSeconds();
        assertThat(elapsed).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldStopAndPreserveTime() throws InterruptedException {
        ResponseTimer timer = new ResponseTimer();
        timer.start();
        Thread.sleep(500);
        timer.stop();
        long stopped = timer.elapsedSeconds();
        Thread.sleep(500);
        assertThat(timer.elapsedSeconds()).isEqualTo(stopped);
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `mvn test -pl . -Dtest=ResponseTimerTest -q`
Expected: Compilation FAIL — `ResponseTimer` class not found.

- [ ] **Step 3: Implement ResponseTimer**

Create `src/main/java/com/lavendercode/chat/terminal/ResponseTimer.java`:

```java
package com.lavendercode.chat.terminal;

/**
 * Nanosecond-precision response timer. Thread-safe via volatile fields.
 */
public final class ResponseTimer {
    private volatile long startNanos;
    private volatile long stopNanos;

    public ResponseTimer() {
        this.startNanos = 0;
        this.stopNanos = 0;
    }

    public void start() {
        this.startNanos = System.nanoTime();
    }

    public void stop() {
        this.stopNanos = System.nanoTime();
    }

    public long elapsedSeconds() {
        long end = stopNanos > 0 ? stopNanos : System.nanoTime();
        if (startNanos == 0) return 0;
        return (end - startNanos) / 1_000_000_000;
    }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `mvn test -pl . -Dtest=ResponseTimerTest -q`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/ResponseTimer.java \
        src/test/java/com/lavendercode/chat/terminal/ResponseTimerTest.java
git commit -m "feat: add ResponseTimer for response timing (F12)"
```

---

### Task 8: Create MarkdownRenderer

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/MarkdownRenderer.java`
- Create: `src/test/java/com/lavendercode/chat/terminal/MarkdownRendererTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/lavendercode/chat/terminal/MarkdownRendererTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import org.jline.utils.AttributedStyle;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @Test
    void shouldRenderPlainText() {
        List<RenderedLine> result = MarkdownRenderer.render("hello world", 80);
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldRenderBoldText() {
        List<RenderedLine> result = MarkdownRenderer.render("**bold** text", 80);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).segments().get(0).getStyle().isBold()).isTrue();
    }

    @Test
    void shouldRenderItalicText() {
        List<RenderedLine> result = MarkdownRenderer.render("*italic* text", 80);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).segments().get(0).getStyle().isItalic()).isTrue();
    }

    @Test
    void shouldRenderCodeBlock() {
        String md = """
            ```java
            System.out.println("hi");
            ```""";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result).hasSize(2); // language tag + code line
    }

    @Test
    void shouldRenderHeadings() {
        List<RenderedLine> result = MarkdownRenderer.render("# Title", 80);
        assertThat(result).hasSize(1);
        // Heading should be bold
        assertThat(result.get(0).segments().get(0).getStyle().isBold()).isTrue();
    }

    @Test
    void shouldRenderBulletList() {
        String md = """
            - item 1
            - item 2""";
        List<RenderedLine> result = MarkdownRenderer.render(md, 80);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).segments().get(0).toString()).contains("•");
    }

    @Test
    void shouldRenderStrikethrough() {
        List<RenderedLine> result = MarkdownRenderer.render("~~deleted~~", 80);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).segments().get(0).getStyle().isCrossedOut()).isTrue();
    }

    @Test
    void shouldWrapAtWidth() {
        String longText = "a".repeat(200);
        List<RenderedLine> result = MarkdownRenderer.render(longText, 40);
        assertThat(result).hasSize(5); // 200/40 = 5 lines
    }

    @Test
    void shouldHandleEmptyInput() {
        List<RenderedLine> result = MarkdownRenderer.render("", 80);
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `mvn test -pl . -Dtest=MarkdownRendererTest -q`
Expected: Compilation FAIL — `MarkdownRenderer` class not found.

- [ ] **Step 3: Implement MarkdownRenderer**

Create `src/main/java/com/lavendercode/chat/terminal/MarkdownRenderer.java`:

```java
package com.lavendercode.chat.terminal;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.ext.strikethrough.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts markdown text to a list of styled {@link RenderedLine}s
 * using flexmark-java for parsing and JLine AttributedStyle for styling.
 */
public final class MarkdownRenderer {

    private static final Parser PARSER;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
        ));
        PARSER = Parser.builder(options).build();
    }

    private MarkdownRenderer() {}

    /**
     * Render markdown text to styled lines at the given display width.
     */
    public static List<RenderedLine> render(String markdown, int width) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }

        Document doc = PARSER.parse(markdown);
        LineAccumulator acc = new LineAccumulator(width);
        walkNode(doc, acc);
        return acc.lines;
    }

    private static void walkNode(Node node, LineAccumulator acc) {
        if (node instanceof Paragraph) {
            walkChildren(node, acc);
        } else if (node instanceof Heading) {
            acc.pushStyle(HEADING_STYLE);
            walkChildren(node, acc);
            acc.popStyle();
            acc.finishLine();
        } else if (node instanceof StrongEmphasis) {
            acc.pushStyle(BOLD_STYLE);
            walkChildren(node, acc);
            acc.popStyle();
        } else if (node instanceof Emphasis) {
            acc.pushStyle(ITALIC_STYLE);
            walkChildren(node, acc);
            acc.popStyle();
        } else if (node instanceof Code) {
            acc.pushStyle(CODE_BG_STYLE);
            acc.append(node.getChars().toString());
            acc.popStyle();
        } else if (node instanceof FencedCodeBlock fcb) {
            acc.pushStyle(CODE_BG_STYLE);
            String info = fcb.getInfo() != null ? fcb.getInfo().toString() : "";
            if (!info.isEmpty()) {
                acc.pushStyle(LANG_TAG_STYLE);
                acc.append(info);
                acc.popStyle();
                acc.finishLine();
            }
            String code = fcb.getContentChars().toString();
            for (String line : code.split("\n", -1)) {
                acc.append(line);
                acc.finishLine();
            }
            acc.popStyle();
        } else if (node instanceof IndentedCodeBlock) {
            acc.pushStyle(CODE_BG_STYLE);
            for (String line : node.getChars().toString().split("\n", -1)) {
                acc.append(line);
                acc.finishLine();
            }
            acc.popStyle();
        } else if (node instanceof BulletListItem) {
            acc.append("  • ");
            walkChildren(node, acc);
            acc.finishLine();
        } else if (node instanceof OrderedListItem) {
            acc.append("  " + ((OrderedListItem) node).getItemNumber() + ". ");
            walkChildren(node, acc);
            acc.finishLine();
        } else if (node instanceof BlockQuote) {
            acc.setBlockPrefix("│ ");
            walkChildren(node, acc);
            acc.setBlockPrefix(null);
            acc.finishLine();
        } else if (node instanceof Strikethrough) {
            acc.pushStyle(STRIKE_STYLE);
            walkChildren(node, acc);
            acc.popStyle();
        } else if (node instanceof com.vladsch.flexmark.ast.Text) {
            acc.append(node.getChars().toString());
        } else if (node instanceof SoftLineBreak) {
            acc.append(" ");
        } else if (node instanceof HardLineBreak) {
            acc.finishLine();
        } else if (node instanceof ThematicBreak) {
            acc.finishLine();
            acc.append("─".repeat(acc.width));
            acc.finishLine();
        } else if (node instanceof TableBlock || node instanceof TableHead
                || node instanceof TableBody || node instanceof TableRow
                || node instanceof TableCell) {
            walkChildren(node, acc);
        } else {
            walkChildren(node, acc);
        }
    }

    private static void walkChildren(Node node, LineAccumulator acc) {
        Node child = node.getFirstChild();
        while (child != null) {
            walkNode(child, acc);
            child = child.getNext();
        }
    }

    // --- Style constants ---
    private static final AttributedStyle BASE_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
    private static final AttributedStyle HEADING_STYLE = BASE_STYLE.bold().foreground(205, 0, 205);
    private static final AttributedStyle BOLD_STYLE = BASE_STYLE.bold();
    private static final AttributedStyle ITALIC_STYLE = BASE_STYLE.italic();
    private static final AttributedStyle CODE_BG_STYLE = BASE_STYLE.background(236, 236, 236).foreground(AttributedStyle.WHITE);
    private static final AttributedStyle LANG_TAG_STYLE = BASE_STYLE.foreground(136, 136, 136);
    private static final AttributedStyle STRIKE_STYLE = BASE_STYLE.crossedOut();

    // --- Internal line accumulator ---
    private static class LineAccumulator {
        final List<RenderedLine> lines = new ArrayList<>();
        final int width;
        StringBuilder currentLine = new StringBuilder();
        List<AttributedString> currentSegments = new ArrayList<>();
        AttributedStyle currentStyle = BASE_STYLE;
        String blockPrefix;

        LineAccumulator(int width) { this.width = width; }

        void pushStyle(AttributedStyle s) { currentStyle = s; }
        void popStyle() { currentStyle = BASE_STYLE; }
        void setBlockPrefix(String p) { this.blockPrefix = p; }

        void append(String text) {
            if (text == null || text.isEmpty()) return;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    finishLine();
                } else {
                    currentLine.append(c);
                    if (displayWidth() >= width) {
                        // wrap — finish current segment and line
                        addSegment();
                        finishLine();
                    }
                }
            }
        }

        int displayWidth() {
            int w = 0;
            if (blockPrefix != null) w += blockPrefix.length();
            for (AttributedString seg : currentSegments) {
                String s = seg.toString();
                for (int i = 0; i < s.length(); i++) {
                    w += charWidth(s.charAt(i));
                }
            }
            for (int i = 0; i < currentLine.length(); i++) {
                w += charWidth(currentLine.charAt(i));
            }
            return w;
        }

        void finishLine() {
            addSegment();
            if (currentSegments.isEmpty() && currentLine.isEmpty()) {
                // empty line — produce blank
                currentSegments.add(new AttributedString("", BASE_STYLE));
            }
            List<AttributedString> copy = List.copyOf(currentSegments);
            String prefixStr = blockPrefix != null ? blockPrefix : "";
            if (!prefixStr.isEmpty()) {
                List<AttributedString> prefixed = new ArrayList<>();
                prefixed.add(new AttributedString(prefixStr, BASE_STYLE));
                prefixed.addAll(copy);
                lines.add(new RenderedLine(prefixed));
            } else {
                lines.add(new RenderedLine(copy));
            }
            currentSegments.clear();
            currentLine.setLength(0);
        }

        void addSegment() {
            if (currentLine.length() > 0) {
                currentSegments.add(new AttributedString(currentLine.toString(), currentStyle));
                currentLine.setLength(0);
            }
        }

        int charWidth(int cp) {
            if (cp >= 0x4E00 && cp <= 0x9FFF) return 2;   // CJK
            if (cp >= 0xAC00 && cp <= 0xD7AF) return 2;     // Hangul
            if (cp >= 0xFF01 && cp <= 0xFF60) return 2;     // Fullwidth
            return 1;
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl . -Dtest=MarkdownRendererTest -q`
Expected: All 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/MarkdownRenderer.java \
        src/test/java/com/lavendercode/chat/terminal/MarkdownRendererTest.java
git commit -m "feat: add flexmark-based MarkdownRenderer (F8)"
```

---

### Task 9: Update TerminalRenderer — 3-column Status Bar + Markdown on Finalize

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/TerminalRenderer.java`

- [ ] **Step 1: Update TerminalRenderer fields and constructor**

Add `providerName` field and update constructor:

```java
// Add alongside modelName field (line 22)
private String providerName = "";

// Update constructor (around line 38-46) to accept providerName
public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                        Theme theme, String providerName, String modelName,
                        InputAreaLayout inputLayout) {
    this.terminal = terminal;
    this.renderQueue = renderQueue;
    this.blocks = new ArrayList<>();
    this.theme = theme;
    this.providerName = providerName != null ? providerName : "";
    this.modelName = modelName != null ? modelName : "";
    this.inputLayout = inputLayout;
    recalcLayout();
}
```

- [ ] **Step 2: Replace drawStatusBar() with 3-column layout**

Replace lines 234-243 (drawStatusBar method):

```java
    private void drawStatusBar() {
        int w = terminal.getWidth();
        int colW = w / 3;

        String left = providerName.isEmpty() ? modelName : providerName;
        if (left.length() > colW - 1) left = left.substring(0, colW - 1);

        String mid = statusText != null ? statusText : "";
        if (mid.length() > colW - 2) mid = mid.substring(0, colW - 2);

        String right = modelName;
        if (right.length() > colW - 1) right = right.substring(0, colW - 1);

        int midPad = Math.max(0, colW - mid.length());
        int leftPad = midPad / 2;
        int rightPad = midPad - leftPad;

        String bar = String.format("%-" + colW + "s│%" + leftPad + "s%s%" + rightPad + "s│%" + colW + "s",
            left, "", mid, "", right);

        AttributedString styled = theme.apply(StyleCatalog.STATUS_BAR, bar);
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.puts(InfoCmp.Capability.clr_eol);
        terminal.writer().print(styled.toAnsi(terminal));
        terminal.flush();
    }
```

Add `statusText` field:

```java
// Add alongside tokenCount (line 23)
private String statusText = null;
```

- [ ] **Step 3: Update dispatch() for StatusUpdate and FinalizeMessage**

In `dispatch()`, replace the `StatusUpdate` case (line 135-139):

```java
            case RenderEvent.StatusUpdate(var pn, var mn, var st, int tc) -> {
                this.providerName = pn;
                this.modelName = mn;
                this.statusText = st;
                this.tokenCount = tc;
                drawStatusBar();
            }
```

Replace the `FinalizeMessage` case (lines 97-102):

```java
            case RenderEvent.FinalizeMessage() -> {
                if (currentAIBlock != null) {
                    String rawText = currentAIBlock.getRawText();
                    List<RenderedLine> styled = MarkdownRenderer.render(rawText, computeContentWidth());
                    currentAIBlock.replaceLines(styled);
                    currentAIBlock.markComplete();
                    currentAIBlock = null;
                    flatCacheDirty = true;
                    drawFull();
                }
            }
```

Remove the `ThinkDelta` case (line 108).

- [ ] **Step 4: Add computeContentWidth helper**

Add to TerminalRenderer:

```java
    private int computeContentWidth() {
        return terminal.getWidth() - 4; // subtract role prefix width
    }
```

- [ ] **Step 5: Update the initial StatusUpdate call**

In `run()` around line 68, after `drawFull()`, update initial status:

```java
        safePut(new RenderEvent.StatusUpdate(providerName, modelName, "", 0));
```

Wait — `run()` doesn't have `safePut`. The initial status is sent via the render queue from outside. We'll handle this in the TerminalChatApplication task.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/TerminalRenderer.java
git commit -m "feat: 3-column status bar with timer, markdown rendering on FinalizeMessage (F7/F8/F12)"
```

---

### Task 10: Update MessageBlock — add getRawText() and replaceLines()

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/MessageBlock.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/MessageBlockTest.java`

- [ ] **Step 1: Add methods to MessageBlock.java**

Add `getRawText()` method:

```java
    /**
     * Returns the concatenated raw text of all content segments,
     * suitable for markdown re-rendering.
     */
    public String getRawText() {
        StringBuilder sb = new StringBuilder();
        for (Segment seg : segments) {
            if (seg instanceof ContentSegment cs) {
                sb.append(cs.rawText);
            }
        }
        return sb.toString();
    }
```

Add `replaceLines(List<RenderedLine>)` method:

```java
    /**
     * Replaces the cached rendered lines with externally-styled lines
     * (e.g. from markdown rendering), bypassing wrapAndColor.
     */
    public void replaceLines(List<RenderedLine> newLines) {
        cachedLines = List.copyOf(newLines);
        cachedLineCount = newLines.size();
        linesDirty = false;
    }
```

- [ ] **Step 2: Write unit test in MessageBlockTest**

```java
    @Test
    void getRawTextShouldReturnAllContent() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("hello ", 80);
        block.append("world", 80);
        assertThat(block.getRawText()).isEqualTo("hello world");
    }

    @Test
    void replaceLinesShouldOverrideCache() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("original", 80);
        List<RenderedLine> newLines = List.of(
            new RenderedLine(new AttributedString("replaced", AttributedStyle.DEFAULT))
        );
        block.replaceLines(newLines);
        assertThat(block.allLines()).hasSize(1);
        assertThat(block.allLines().get(0).segments().get(0).toString()).isEqualTo("replaced");
    }
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl . -Dtest=MessageBlockTest -q`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/MessageBlock.java \
        src/test/java/com/lavendercode/chat/terminal/MessageBlockTest.java
git commit -m "feat: add getRawText() and replaceLines() to MessageBlock"
```

---

### Task 11: Update InputSystem — Ctrl+C → EXIT

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/InputSystem.java`
- Modify: `src/main/java/com/lavendercode/chat/terminal/TerminalInput.java`
- Modify: `src/main/java/com/lavendercode/chat/terminal/TerminalKeyReader.java`

- [ ] **Step 1: Add Exiting event to TerminalInput.java**

Add a new variant to `TerminalInput`:

```java
sealed interface TerminalInput {

    record Character(int code) implements TerminalInput {}

    record Scroll(String command) implements TerminalInput {}

    record Paste(String text) implements TerminalInput {}

    record Submit() implements TerminalInput {}

    record Newline() implements TerminalInput {}

    /** Signals that the user wants to exit (Ctrl+C). */
    record Exit() implements TerminalInput {}
}
```

- [ ] **Step 2: Update InputSystem.java Ctrl+C handling**

In `readEditedLine()`, change the Ctrl+C block (lines 118-122):

```java
                    if (code == 3) {  // Ctrl+C → exit
                        publishDraftSync("", 0);
                        shutdown.set(true);
                        inputQueue.offer(new InputEvent.Shutdown());
                        return null;
                    }
```

- [ ] **Step 3: Update TerminalKeyReader.java — Alt+Enter recognition**

In `readEscapeSequence()`, add Alt+Enter detection before the existing CSI parser. Find the method and add at the top after reading `next`:

```java
    private TerminalInput readEscapeSequence() throws IOException {
        int next = timedRead();
        if (next < 0) return new TerminalInput.Character(0x1B);
        if (next == '\n' || next == '\r') {
            // Alt+Enter → newline without submit
            if (next == '\r') consumeLfIfPresent();
            return new TerminalInput.Newline();
        }
        // ... rest of existing implementation unchanged
```

- [ ] **Step 4: Run compilation check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/InputSystem.java \
        src/main/java/com/lavendercode/chat/terminal/TerminalInput.java \
        src/main/java/com/lavendercode/chat/terminal/TerminalKeyReader.java
git commit -m "feat: Ctrl+C exits, Alt+Enter inserts newline (F9/F10)"
```

---

### Task 12: Create ProviderSelector

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/ProviderSelector.java`

- [ ] **Step 1: Implement ProviderSelector**

Create `src/main/java/com/lavendercode/chat/terminal/ProviderSelector.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.ProviderConfig;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.List;

/**
 * Terminal-based provider selection UI with arrow-key navigation.
 * Blocks until the user makes a selection or exits.
 */
public final class ProviderSelector {

    private static final String VERSION = "2.0.0";
    private static final AttributedStyle TITLE_STYLE =
        AttributedStyle.DEFAULT.foreground(205, 133, 255).bold();
    private static final AttributedStyle SELECTED_STYLE =
        AttributedStyle.DEFAULT.foreground(205, 133, 255).bold();
    private static final AttributedStyle NORMAL_STYLE =
        AttributedStyle.DEFAULT.foreground(200, 200, 200);
    private static final AttributedStyle HINT_STYLE =
        AttributedStyle.DEFAULT.foreground(136, 136, 136);
    private static final AttributedStyle CWD_STYLE =
        AttributedStyle.DEFAULT.foreground(136, 136, 136);
    private static final AttributedStyle BG_STYLE =
        AttributedStyle.DEFAULT.background(14, 10, 24);
    private static final AttributedStyle BODY_STYLE =
        AttributedStyle.DEFAULT.foreground(230, 220, 255);

    private ProviderSelector() {}

    /**
     * Display the selection UI and return the user's chosen provider.
     * Blocks until Enter is pressed. Ctrl+C exits the JVM.
     */
    public static ProviderConfig select(Terminal terminal, List<ProviderConfig> providers) {
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();

        int selectedIndex = 0;
        try {
            while (true) {
                drawScreen(terminal, providers, selectedIndex);
                int ch = terminal.reader().read();
                if (ch == '\r' || ch == '\n') {
                    // Enter — confirm selection
                    break;
                } else if (ch == 3) {
                    // Ctrl+C — exit
                    terminal.puts(InfoCmp.Capability.cursor_visible);
                    terminal.puts(InfoCmp.Capability.exit_ca_mode);
                    terminal.flush();
                    System.exit(0);
                } else if (ch == 0x1B) {
                    // Escape sequence — read arrow keys
                    int next = terminal.reader().read(50);
                    if (next == '[') {
                        int dir = terminal.reader().read();
                        if (dir == 'A') {
                            selectedIndex = Math.max(0, selectedIndex - 1);
                        } else if (dir == 'B') {
                            selectedIndex = Math.min(providers.size() - 1, selectedIndex + 1);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Fall through to return first provider
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.flush();
        }

        return providers.get(selectedIndex);
    }

    private static void drawScreen(Terminal terminal, List<ProviderConfig> providers, int selected) {
        int w = terminal.getWidth();
        terminal.puts(InfoCmp.Capability.clear_screen);

        // Title
        printCentered(terminal, 0, "LavenderCode v" + VERSION, w, TITLE_STYLE);
        printCentered(terminal, 1, "cwd: " + truncatePath(System.getProperty("user.dir"), w - 6), w, CWD_STYLE);
        printCentered(terminal, 3, "\u2500".repeat(Math.min(w, 80)), w, HINT_STYLE);
        printCentered(terminal, 5, "Select AI Provider:", w, BODY_STYLE);

        // Provider list
        for (int i = 0; i < providers.size(); i++) {
            ProviderConfig pc = providers.get(i);
            String displayName = pc.name() != null ? pc.name() : pc.protocol() + "-" + pc.model();
            String line = (i == selected ? "  ● " : "    ") + displayName + " (" + pc.model() + ")";
            AttributedStyle style = i == selected ? SELECTED_STYLE : NORMAL_STYLE;
            terminal.puts(InfoCmp.Capability.cursor_address, 7 + i, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().print(new AttributedString(line, style).toAnsi(terminal));
        }

        // Hint
        String hint = "\u2191\u2193 select  Enter confirm  Ctrl+C exit";
        terminal.puts(InfoCmp.Capability.cursor_address, 9 + providers.size(), 0);
        terminal.writer().print(new AttributedString(hint, HINT_STYLE).toAnsi(terminal));

        terminal.flush();
    }

    private static void printCentered(Terminal terminal, int row, String text, int width,
                                       AttributedStyle style) {
        int pad = Math.max(0, (width - displayWidth(text)) / 2);
        String padded = " ".repeat(pad) + text;
        terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
        terminal.puts(InfoCmp.Capability.clr_eol);
        terminal.writer().print(new AttributedString(padded, style).toAnsi(terminal));
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            if (cp >= 0x4E00 && cp <= 0x9FFF) w += 2;
            else if (cp >= 0xAC00 && cp <= 0xD7AF) w += 2;
            else if (cp >= 0xFF01 && cp <= 0xFF60) w += 2;
            else w += 1;
        }
        return w;
    }

    private static String truncatePath(String path, int maxWidth) {
        if (displayWidth(path) <= maxWidth) return path;
        String prefix = "…";
        int remaining = maxWidth - prefix.length();
        return prefix + path.substring(Math.max(0, path.length() - remaining));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/ProviderSelector.java
git commit -m "feat: add ProviderSelector with arrow-key navigation (F2)"
```

---

### Task 13: Update LavenderSplash — Add Version and CWD

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/LavenderSplash.java`

- [ ] **Step 1: Add version and CWD to LavenderSplash.show()**

Add constant:

```java
    private static final String VERSION = "2.0.0";
```

After the existing typewriter tagline + hold, add the version/CWD output. Find the end of the `show()` method (after `Thread.sleep(HOLD_MS)` line in the delay block) and add:

```java
        // Show application info
        int termWidth = terminal.getSize().getColumns();
        terminal.writer().println();
        terminal.writer().println(center("LavenderCode v" + VERSION, termWidth));
        String cwd = "cwd: " + System.getProperty("user.dir");
        if (cwd.length() > termWidth) {
            cwd = "cwd: …" + cwd.substring(Math.max(0, cwd.length() - termWidth + 6));
        }
        terminal.writer().println(center(cwd, termWidth));
        terminal.writer().println("─".repeat(Math.min(termWidth, 80)));
        terminal.flush();
```

Add a static `center()` helper:

```java
    private static String center(String text, int width) {
        int displayW = displayWidth(text);
        int pad = Math.max(0, (width - displayW) / 2);
        return " ".repeat(pad) + text;
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/LavenderSplash.java
git commit -m "feat: add version and CWD to startup splash (F7)"
```

---

### Task 14: Update TerminalChatApplication — Inject ProviderConfig + providerName

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java`

- [ ] **Step 1: Update constructor and fields**

Add `providerName` field and update constructor:

```java
public class TerminalChatApplication {

    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String providerName;
    private final String modelName;
    private final LlmConfig config;
    private final Theme theme;

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String providerName,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme) {
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.providerName = providerName;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
    }
```

Update `TerminalRenderer` construction in `run()` (line ~51):

```java
        TerminalRenderer renderer = new TerminalRenderer(
            terminal, renderQueue, theme, providerName, modelName, inputLayout);
```

Update `NetworkOrchestrator` construction (line ~46):

```java
        NetworkOrchestrator orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, providerName, modelName, config, timerScheduler
        );
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java
git commit -m "refactor: inject ProviderConfig name into TerminalChatApplication"
```

---

### Task 15: Update NetworkOrchestrator — providerName + ResponseTimer

**Files:**
- Modify: `src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java`
- Modify: `src/test/java/com/lavendercode/chat/terminal/NetworkOrchestratorTest.java`

- [ ] **Step 1: Update fields and constructor**

Add `providerName` and `timerScheduler` fields:

```java
    private final String providerName;
    private final java.util.concurrent.ScheduledExecutorService timerScheduler;
    private volatile java.util.concurrent.ScheduledFuture<?> timerTask;
```

Update constructor:

```java
    public NetworkOrchestrator(ChatService chatService, DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String providerName, String modelName, LlmConfig config,
                               java.util.concurrent.ScheduledExecutorService timerScheduler) {
        // ... existing assignments ...
        this.providerName = providerName;
        this.timerScheduler = timerScheduler;
    }
```

Update initial status in `run()`:

```java
    public void run() {
        safePut(new RenderEvent.StatusUpdate(providerName, modelName, "", 0));
        // ...
```

- [ ] **Step 2: Integrate ResponseTimer into handleSendMessage**

In `handleSendMessage`, after `currentRequest.set(ctxRef.get())`:

```java
        ResponseTimer timer = new ResponseTimer();
        timer.start();

        // Schedule timer updates every 1 second
        timerTask = timerScheduler.scheduleAtFixedRate(() -> {
            safePut(new RenderEvent.StatusUpdate(
                providerName, modelName,
                "Imagining… (" + timer.elapsedSeconds() + "s)", 0));
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
```

In `onDeltaReceived`, for the `Complete` and `Error` cases, stop the timer:

```java
            case DeltaEvent.Complete() -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    timer.stop();
                    safePut(new RenderEvent.StatusUpdate(
                        providerName, modelName,
                        "Done (" + timer.elapsedSeconds() + "s)", 0));
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.FinalizeMessage());
                    // Clear timer text after 1 second
                    timerScheduler.schedule(() -> safePut(
                        new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                        1, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    cancelTimer();
                    timer.stop();
                    safePut(new RenderEvent.StatusUpdate(
                        providerName, modelName,
                        "Error (" + timer.elapsedSeconds() + "s)", 0));
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                    timerScheduler.schedule(() -> safePut(
                        new RenderEvent.StatusUpdate(providerName, modelName, "", 0)),
                        2, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
```

Add helper method:

```java
    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
    }
```

Also update `Usage` case in `onDeltaReceived`:

```java
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(providerName, modelName, null, i + o));
```

- [ ] **Step 3: Update handleShutdown to cancel timer**

```java
    private void handleShutdown() {
        cancelTimer();
        // ... existing code
    }
```

- [ ] **Step 4: Update NetworkOrchestratorTest constructor calls**

Update test to pass new constructor parameters (`providerName` and `timerScheduler`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java \
        src/test/java/com/lavendercode/chat/terminal/NetworkOrchestratorTest.java
git commit -m "feat: integrate ResponseTimer and providerName into NetworkOrchestrator (F12)"
```

---

### Task 16: Update LavenderCode Entry Point — Multi-Provider + Selector

**Files:**
- Modify: `src/main/java/com/lavendercode/LavenderCode.java`
- Modify: `config.yaml` (if it exists, or create config.yaml.example)

- [ ] **Step 1: Rewrite LavenderCode.main()**

Replace the entire file:

```java
package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.ProviderSelector;
import com.lavendercode.chat.terminal.TerminalChatApplication;
import com.lavendercode.chat.terminal.Theme;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;

public class LavenderCode {

    public static void main(String[] args) throws Exception {
        Path configPath = Path.of("config.yaml");

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            }
        }

        LlmConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
            if (!java.nio.file.Files.exists(configPath)) {
                System.err.println("Run: copy config.yaml.example config.yaml");
                System.err.println("Then edit config.yaml with your API key.");
            }
            System.exit(1);
            return;
        }

        // Provider selection
        Terminal terminal = TerminalBuilder.builder()
            .name("LavenderCode")
            .system(true)
            .build();

        ProviderConfig selectedProvider;
        if (config.providers().size() == 1) {
            selectedProvider = config.providers().get(0);
        } else {
            selectedProvider = ProviderSelector.select(terminal, config.providers());
        }

        String providerName = selectedProvider.name() != null
            ? selectedProvider.name()
            : selectedProvider.protocol() + "-" + selectedProvider.model();

        LlmProvider provider = ProviderRegistry.get(selectedProvider.protocol());
        SessionManager sessionManager = new InMemorySessionManager();

        TerminalChatApplication app = new TerminalChatApplication(
            sessionManager, provider,
            providerName, selectedProvider.model(), config,
            Theme.dark()
        );
        app.run(terminal);
    }
}
```

- [ ] **Step 2: Update config.yaml**

Update `config.yaml` to the new providers-list format:

```yaml
providers:
  - name: DeepSeek
    protocol: openai
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: sk-355a51eed83842ceb04230c48f2a3532

options:
  max_tokens: 4096
  system_prompt: "You are a helpful AI assistant."
```

- [ ] **Step 3: Compile and verify**

Run: `mvn compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lavendercode/LavenderCode.java \
        src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java \
        src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java \
        config.yaml
git commit -m "feat: integrate multi-provider selection into entry point (F1/F2)"
```

---

### Task 17: Fix Remaining Compilation Errors — Wiring Check

**Files:**
- All files with remaining compilation errors from the refactoring

- [ ] **Step 1: Run full compilation**

Run: `mvn compile -q`
Expected: May have errors. Fix each one by one.

- [ ] **Step 2: Fix TerminalChatIntegrationTest**

The integration test uses `DumbTerminal` and constructs components manually. Update constructor calls for:
- `TerminalChatApplication` — add `providerName` parameter
- `TerminalRenderer` — add `providerName` parameter
- `NetworkOrchestrator` — add `providerName` and `timerScheduler` parameters
- Replace `config.provider()` calls with `config.providers().get(0)`
- Replace `new LlmConfig(...)` single-provider constructors with list

- [ ] **Step 3: Fix all remaining unit tests**

Run: `mvn test-compile -q`
Expected: Identify all broken tests. Fix constructor calls and `config.provider()` → `config.providers().get(0)`.

- [ ] **Step 4: Run full test suite**

Run: `mvn test -q`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "fix: update all tests and wiring for v2 refactoring"
```

---

### Task 18: Final Integration Validation

- [ ] **Step 1: Run full test suite**

```bash
mvn clean test -q
```
Expected: All tests PASS.

- [ ] **Step 2: Package the application**

```bash
mvn package -DskipTests -q
```
Expected: BUILD SUCCESS, JAR created in `target/`.

- [ ] **Step 3: Verify entry point**

```bash
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar --help 2>&1 || true
```
Expected: Application starts (or exits with config error if no config).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: final integration validation for v2"
```

---

## Spec Coverage Verification

| Requirement | Task(s) | Status |
|-------------|---------|--------|
| F1 — Config loading | Task 2 | Covered |
| F2 — Provider selection | Task 12, Task 16 | Covered |
| F3 — Multi-protocol | Task 6 | Covered |
| F4 — Send chat request | Task 6 | Covered |
| F5 — Stream receive (discard thinking) | Task 5 | Covered |
| F6 — Multi-turn context | (unchanged, pre-existing) | Covered |
| F7 — Terminal UI layout | Task 9, Task 13 | Covered |
| F8 — Markdown rendering | Task 8, Task 10 | Covered |
| F9 — Input/submit (Alt+Enter) | Task 11 | Covered |
| F10 — Exit (Ctrl+C) | Task 11 | Covered |
| F11 — Error feedback | (unchanged, pre-existing) | Covered |
| F12 — Response timing | Task 7, Task 15 | Covered |
| N1 — Non-blocking UI | (unchanged, pre-existing 4-thread) | Covered |
| N2 — Streaming real-time | Task 15 | Covered |
| N3 — Cross-protocol consistency | Task 6 | Covered |
| N4 — Config robustness | Task 2 | Covered |
| N5 — Key security | (unchanged, pre-existing) | Covered |
| N6 — Terminal compatibility | (unchanged + markdown reflow) | Covered |
| N7 — Clean exit | (unchanged, pre-existing finally block) | Covered |
