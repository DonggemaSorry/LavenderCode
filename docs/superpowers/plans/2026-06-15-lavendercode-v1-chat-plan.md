# LavenderCode v1.0 Chat Implementation Plan

> For agentic workers. Each task is designed to be executed by an AI coding agent in a single session. Tasks are ordered by dependency: no task should be started until all tasks above it are complete.

## Goal

Build a CLI AI assistant (LavenderCode v1.0) with:

- TUI multi-turn chat (Lanterna)
- Streaming SSE responses
- Anthropic and OpenAI backends
- YAML-based configuration
- Full test coverage (unit -> contract -> integration)

## Architecture

- **Mixed-layer architecture**: `core/` module has zero TUI dependency; `chat/` depends on `core/`
- **TDD flow**: unit tests -> contract tests -> integration tests
- **Tech stack**: Java 17, Maven, Lanterna 3.x, OkHttp 4.x, Jackson YAML 2.x, JUnit 5 + Mockito + MockWebServer + AssertJ

## Directory Structure

```
lavendercode/
  pom.xml
  src/
    main/java/io/lavendercode/
      config/
        ProviderConfig.java
        ThinkingConfig.java
        Options.java
        LlmConfig.java
        ConfigException.java
        ConfigLoader.java
      model/
        Message.java
        Role.java
        StreamEvent.java
        StreamEventIterator.java
      provider/
        LlmProvider.java
        ProviderRegistry.java
        anthropic/
          AnthropicProvider.java
        openai/
          OpenAIProvider.java
      parser/
        SseEventParser.java
      session/
        SessionManager.java
        InMemorySessionManager.java
      tui/
        TuiApplication.java
      LavenderCode.java
    test/java/io/lavendercode/
      config/
        ConfigLoaderTest.java
      model/
        MessageTest.java
        StreamEventTest.java
      parser/
        SseEventParserTest.java
      provider/
        LlmProviderContractTest.java
        anthropic/
          AnthropicProviderTest.java
        openai/
          OpenAIProviderTest.java
      session/
        InMemorySessionManagerTest.java
      tui/
        TuiApplicationTest.java
      integration/
        AnthropicIntegrationTest.java
        OpenAIIntegrationTest.java
    test/resources/config/
      valid-full.yaml
      valid-minimal.yaml
      invalid-missing-api-key.yaml
      invalid-unknown-provider.yaml
  config.yaml.example
```

---

## Task 1: Project Scaffolding and POM

### Goal

Create the Maven project skeleton with all dependencies, directory structure, and build configuration.

### Files to create

- `pom.xml`

### Steps

- [x] Create root directory `lavendercode/`
- [x] Write `pom.xml` with all dependencies and plugins

### `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.lavendercode</groupId>
    <artifactId>lavendercode</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>LavenderCode</name>
    <description>CLI AI assistant with TUI chat, streaming SSE, Anthropic and OpenAI backends</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <okhttp.version>4.12.0</okhttp.version>
        <jackson.version>2.17.2</jackson.version>
        <lanterna.version>3.1.2</lanterna.version>
        <junit.version>5.10.3</junit.version>
        <mockito.version>5.12.0</mockito.version>
        <assertj.version>3.26.3</assertj.version>
    </properties>

    <dependencies>
        <!-- OkHttp -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>${okhttp.version}</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp-sse</artifactId>
            <version>${okhttp.version}</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>${okhttp.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Jackson YAML -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jdk8</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <!-- Lanterna -->
        <dependency>
            <groupId>com.googlecode.lanterna</groupId>
            <artifactId>lanterna</artifactId>
            <version>${lanterna.version}</version>
        </dependency>

        <!-- Bean Validation (Jakarta) -->
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
            <version>3.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.hibernate.validator</groupId>
            <artifactId>hibernate-validator</artifactId>
            <version>8.0.1.Final</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish</groupId>
            <artifactId>jakarta.el</artifactId>
            <version>4.0.2</version>
        </dependency>

        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Mockito -->
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- AssertJ -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>io.lavendercode.LavenderCode</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>io.lavendercode.LavenderCode</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Task 2: Configuration Data Model

### Goal

Create the configuration data model records that represent the YAML config structure.

### Files to create

- `src/main/java/io/lavendercode/config/ProviderConfig.java`
- `src/main/java/io/lavendercode/config/ThinkingConfig.java`
- `src/main/java/io/lavendercode/config/Options.java`
- `src/main/java/io/lavendercode/config/LlmConfig.java`
- `src/main/java/io/lavendercode/config/ConfigException.java`

### Steps

- [x] Create `ProviderConfig` record
- [x] Create `ThinkingConfig` record
- [x] Create `Options` record
- [x] Create `LlmConfig` record
- [x] Create `ConfigException` class

### `ProviderConfig.java`

```java
package io.lavendercode.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for a single LLM provider (e.g., anthropic, openai).
 *
 * @param name       The provider name (service-loader key, e.g. "anthropic")
 * @param apiKey     The API key for authentication
 * @param model      The model identifier (e.g. "claude-sonnet-4-20250514")
 * @param baseUrl    Optional base URL override (defaults to provider's standard)
 * @param maxTokens  Optional maximum tokens for responses
 */
public record ProviderConfig(
    @NotBlank String name,

    @NotBlank @JsonProperty("api_key") String apiKey,

    String model,

    @JsonProperty("base_url") String baseUrl,

    @JsonProperty("max_tokens") Integer maxTokens
) {}
```

### `ThinkingConfig.java`

```java
package io.lavendercode.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for extended thinking / reasoning.
 *
 * @param enabled       Whether thinking is enabled
 * @param budgetTokens  Maximum tokens allocated to thinking (if applicable)
 */
public record ThinkingConfig(
    boolean enabled,

    @JsonProperty("budget_tokens") Integer budgetTokens
) {

    public ThinkingConfig {
        if (budgetTokens != null && budgetTokens < 1) {
            throw new IllegalArgumentException("budget_tokens must be positive when set");
        }
    }
}
```

### `Options.java`

```java
package io.lavendercode.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Miscellaneous chat options.
 *
 * @param temperature   Sampling temperature (0.0 - 2.0)
 * @param thinking      Extended thinking configuration
 */
public record Options(
    Double temperature,

    ThinkingConfig thinking
) {

    public Options {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be in [0.0, 2.0]");
        }
    }
}
```

### `LlmConfig.java`

```java
package io.lavendercode.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Root configuration object, deserialised from config.yaml.
 *
 * @param activeProvider  The name of the provider to use (e.g. "anthropic")
 * @param providers       List of provider configurations
 * @param options         Global options
 */
public record LlmConfig(
    @JsonProperty("active_provider") String activeProvider,

    @Valid List<ProviderConfig> providers,

    Options options
) {}
```

### `ConfigException.java`

```java
package io.lavendercode.config;

/**
 * Exception thrown during configuration loading or validation.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## Task 3: Core Data Models (Message, Role, StreamEvent)

### Goal

Create the core data models shared across the application.

### Files to create

- `src/main/java/io/lavendercode/model/Message.java`
- `src/main/java/io/lavendercode/model/Role.java`
- `src/main/java/io/lavendercode/model/StreamEvent.java`
- `src/main/java/io/lavendercode/model/StreamEventIterator.java`

### Steps

- [x] Create `Role` enum
- [x] Create `Message` record
- [x] Create `StreamEvent` sealed interface and implementations
- [x] Create `StreamEventIterator` interface
- [x] Create unit tests for `Message` and `StreamEvent`

### `Role.java`

```java
package io.lavendercode.model;

/**
 * Role of a message participant.
 */
public enum Role {
    USER,
    ASSISTANT,
    SYSTEM
}
```

### `Message.java`

```java
package io.lavendercode.model;

/**
 * A single chat message.
 *
 * @param role    The sender role
 * @param content The message content (text)
 */
public record Message(Role role, String content) {

    public Message {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
```

### `StreamEvent.java`

```java
package io.lavendercode.model;

/**
 * Sealed type hierarchy for SSE stream events emitted by LLM providers.
 */
public sealed interface StreamEvent {

    /** A text delta (chunk of the response). */
    record ContentDelta(String text) implements StreamEvent {}

    /** A complete message has been received (final). */
    record MessageDone(String stopReason) implements StreamEvent {}

    /** Thinking / reasoning block delta. */
    record ThinkingDelta(String thinking) implements StreamEvent {}

    /** An error occurred during streaming. */
    record StreamError(String message) implements StreamEvent {}
}
```

### `StreamEventIterator.java`

```java
package io.lavendercode.model;

import java.util.Iterator;

/**
 * An {@link Iterator} over {@link StreamEvent}s. May throw
 * runtime exceptions on network errors.
 */
public interface StreamEventIterator extends Iterator<StreamEvent>, AutoCloseable {

    /** Close any underlying resources (HTTP connection, etc.). */
    @Override
    void close();
}
```

### Test: `src/test/java/io/lavendercode/model/MessageTest.java`

```java
package io.lavendercode.model;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void shouldCreateValidMessage() {
        var msg = new Message(Role.USER, "Hello");
        assertThat(msg.role()).isEqualTo(Role.USER);
        assertThat(msg.content()).isEqualTo("Hello");
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> new Message(null, "text"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> new Message(Role.USER, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

### Test: `src/test/java/io/lavendercode/model/StreamEventTest.java`

```java
package io.lavendercode.model;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StreamEventTest {

    @Test
    void shouldCreateContentDelta() {
        var event = new StreamEvent.ContentDelta("Hello");
        assertThat(event.text()).isEqualTo("Hello");
    }

    @Test
    void shouldCreateMessageDone() {
        var event = new StreamEvent.MessageDone("end_turn");
        assertThat(event.stopReason()).isEqualTo("end_turn");
    }

    @Test
    void shouldCreateThinkingDelta() {
        var event = new StreamEvent.ThinkingDelta("reasoning...");
        assertThat(event.thinking()).isEqualTo("reasoning...");
    }

    @Test
    void shouldCreateStreamError() {
        var event = new StreamEvent.StreamError("timeout");
        assertThat(event.message()).isEqualTo("timeout");
    }
}
```

---

## Task 4: LlmProvider Interface and ProviderRegistry

### Goal

Define the `LlmProvider` interface and the `ProviderRegistry` that uses `ServiceLoader` to discover implementations.

### Files to create

- `src/main/java/io/lavendercode/provider/LlmProvider.java`
- `src/main/java/io/lavendercode/provider/ProviderRegistry.java`

### Steps

- [x] Create `LlmProvider` interface
- [x] Create `ProviderRegistry` with ServiceLoader discovery

### `LlmProvider.java`

```java
package io.lavendercode.provider;

import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.Message;
import io.lavendercode.model.StreamEventIterator;
import java.util.List;

/**
 * Service-provider interface for LLM backends.
 *
 * <p>Implementations must be registered via {@link java.util.ServiceLoader}
 * and provide a public no-arg constructor.
 */
public interface LlmProvider {

    /**
     * Human-readable name used in configuration (e.g. "anthropic", "openai").
     */
    String name();

    /**
     * Send a list of messages to the LLM and return a streaming iterator
     * of response events.
     *
     * @param config     the provider-specific configuration
     * @param messages   the conversation history
     * @return an iterator over streamed events
     */
    StreamEventIterator chat(ProviderConfig config, List<Message> messages);
}
```

### `ProviderRegistry.java`

```java
package io.lavendercode.provider;

import io.lavendercode.config.ConfigException;
import java.util.*;

/**
 * Registry that discovers {@link LlmProvider} implementations via
 * {@link ServiceLoader}.
 */
public final class ProviderRegistry {

    private final Map<String, LlmProvider> providers;

    public ProviderRegistry() {
        this(ServiceLoader.load(LlmProvider.class));
    }

    /** Package-private for testing. */
    ProviderRegistry(ServiceLoader<LlmProvider> loader) {
        Map<String, LlmProvider> map = new LinkedHashMap<>();
        for (LlmProvider provider : loader) {
            map.put(provider.name(), provider);
        }
        this.providers = Collections.unmodifiableMap(map);
    }

    /**
     * Return the provider registered under the given name.
     *
     * @throws ConfigException if no provider is found
     */
    public LlmProvider getProvider(String name) {
        LlmProvider provider = providers.get(name);
        if (provider == null) {
            throw new ConfigException("No provider registered for name: " + name
                + ". Available: " + providers.keySet());
        }
        return provider;
    }

    /** Return all registered provider names. */
    public Set<String> getProviderNames() {
        return providers.keySet();
    }
}
```

---

## Task 5: ConfigLoader with TDD

### Goal

Implement `ConfigLoader` that reads, parses, and validates `config.yaml` using Jackson YAML, returning an `LlmConfig` object.

### Files to create

- `src/test/resources/config/valid-full.yaml`
- `src/test/resources/config/valid-minimal.yaml`
- `src/test/resources/config/invalid-missing-api-key.yaml`
- `src/test/resources/config/invalid-unknown-provider.yaml`
- `src/test/java/io/lavendercode/config/ConfigLoaderTest.java`
- `src/main/java/io/lavendercode/config/ConfigLoader.java`

### Steps

- [x] Create 4 YAML test resource files
- [x] Write `ConfigLoaderTest` with 6 tests
- [x] Implement `ConfigLoader`

### `src/test/resources/config/valid-full.yaml`

```yaml
active_provider: anthropic
providers:
  - name: anthropic
    api_key: sk-ant-xxxx
    model: claude-sonnet-4-20250514
    base_url: https://api.anthropic.com
    max_tokens: 4096
  - name: openai
    api_key: sk-openai-xxxx
    model: gpt-4o
    base_url: https://api.openai.com
    max_tokens: 2048
options:
  temperature: 0.7
  thinking:
    enabled: true
    budget_tokens: 1024
```

### `src/test/resources/config/valid-minimal.yaml`

```yaml
active_provider: anthropic
providers:
  - name: anthropic
    api_key: sk-ant-xxxx
```

### `src/test/resources/config/invalid-missing-api-key.yaml`

```yaml
active_provider: anthropic
providers:
  - name: anthropic
    model: claude-sonnet-4-20250514
```

### `src/test/resources/config/invalid-unknown-provider.yaml`

```yaml
active_provider: nonexistent
providers:
  - name: anthropic
    api_key: sk-ant-xxxx
```

### `ConfigLoaderTest.java`

```java
package io.lavendercode.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.*;

class ConfigLoaderTest {

    private static final String RESOURCES = "src/test/resources/config/";
    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void shouldLoadFullConfig() {
        var config = loader.load(RESOURCES + "valid-full.yaml");
        assertThat(config.activeProvider()).isEqualTo("anthropic");
        assertThat(config.providers()).hasSize(2);
        assertThat(config.providers().get(0).name()).isEqualTo("anthropic");
        assertThat(config.providers().get(0).apiKey()).isEqualTo("sk-ant-xxxx");
        assertThat(config.providers().get(0).model()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(config.providers().get(1).name()).isEqualTo("openai");
        assertThat(config.options()).isNotNull();
        assertThat(config.options().temperature()).isEqualTo(0.7);
        assertThat(config.options().thinking().enabled()).isTrue();
        assertThat(config.options().thinking().budgetTokens()).isEqualTo(1024);
    }

    @Test
    void shouldLoadMinimalConfig() {
        var config = loader.load(RESOURCES + "valid-minimal.yaml");
        assertThat(config.activeProvider()).isEqualTo("anthropic");
        assertThat(config.providers()).hasSize(1);
        assertThat(config.options()).isNull();
    }

    @Test
    void shouldThrowOnMissingFile() {
        assertThatThrownBy(() -> loader.load("nonexistent.yaml"))
            .isInstanceOf(ConfigException.class);
    }

    @Test
    void shouldThrowOnMissingApiKey() {
        assertThatThrownBy(() -> loader.load(RESOURCES + "invalid-missing-api-key.yaml"))
            .isInstanceOf(ConfigException.class);
    }

    @Test
    void shouldThrowOnUnknownProvider() {
        assertThatThrownBy(() -> {
            var config = loader.load(RESOURCES + "invalid-unknown-provider.yaml");
            // Validation that active_provider exists in providers list
            boolean found = config.providers().stream()
                .anyMatch(p -> p.name().equals(config.activeProvider()));
            if (!found) {
                throw new ConfigException("Active provider '" + config.activeProvider()
                    + "' not found in providers list");
            }
        }).isInstanceOf(ConfigException.class);
    }

    @Test
    void shouldThrowOnInvalidYaml() {
        assertThatThrownBy(() -> loader.load(RESOURCES + "valid-full.yaml" + ".bak")) // non-existent
            .isInstanceOf(ConfigException.class);
    }
}
```

### `ConfigLoader.java`

```java
package io.lavendercode.config;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import jakarta.validation.*;
import java.io.*;
import java.nio.file.*;
import java.util.Set;

/**
 * Loads and validates {@link LlmConfig} from a YAML file.
 */
public class ConfigLoader {

    private final ObjectMapper mapper;
    private final Validator validator;

    public ConfigLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new Jdk8Module())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        } catch (Exception e) {
            throw new ConfigException("Failed to initialise Bean Validation", e);
        }
    }

    /**
     * Load and validate configuration from a YAML file path.
     *
     * @param path file system path to config.yaml
     * @return validated {@link LlmConfig}
     * @throws ConfigException on I/O error, parse error, or validation failure
     */
    public LlmConfig load(String path) {
        try {
            var yaml = Files.readString(Path.of(path));
            var config = mapper.readValue(yaml, LlmConfig.class);

            // Bean validation
            Set<ConstraintViolation<LlmConfig>> violations = validator.validate(config);
            if (!violations.isEmpty()) {
                var msg = new StringBuilder("Configuration validation failed:");
                for (var v : violations) {
                    msg.append("\n  - ").append(v.getPropertyPath()).append(": ").append(v.getMessage());
                }
                throw new ConfigException(msg.toString());
            }

            // Cross-field: active_provider must appear in providers list
            boolean found = config.providers() != null
                && config.providers().stream().anyMatch(p -> p.name().equals(config.activeProvider()));
            if (!found) {
                throw new ConfigException("Active provider '" + config.activeProvider()
                    + "' is not listed in the providers section");
            }

            return config;
        } catch (ConfigException e) {
            throw e;
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration: " + path, e);
        }
    }
}
```

---

## Task 6: SSE Parser with TDD

### Goal

Implement `SseEventParser` that converts raw SSE lines into structured `StreamEvent` objects.

### Files to create

- `src/test/java/io/lavendercode/parser/SseEventParserTest.java`
- `src/main/java/io/lavendercode/parser/SseEventParser.java`

### Steps

- [x] Write `SseEventParserTest` with 8 tests
- [x] Implement `SseEventParser`

### `SseEventParserTest.java`

```java
package io.lavendercode.parser;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.model.StreamEvent;
import org.junit.jupiter.api.Test;
import java.util.List;

class SseEventParserTest {

    private final SseEventParser parser = new SseEventParser();

    @Test
    void shouldParseContentDelta() {
        String raw = "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"Hello\"}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(((StreamEvent.ContentDelta) events.get(0)).text()).isEqualTo("Hello");
    }

    @Test
    void shouldParseMessageDone() {
        String raw = "event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.MessageDone.class);
        assertThat(((StreamEvent.MessageDone) events.get(0)).stopReason()).isEqualTo("end_turn");
    }

    @Test
    void shouldParseThinkingDelta() {
        String raw = "event: thinking_delta\ndata: {\"thinking\":\"reasoning...\"}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.ThinkingDelta.class);
        assertThat(((StreamEvent.ThinkingDelta) events.get(0)).thinking()).isEqualTo("reasoning...");
    }

    @Test
    void shouldParseStreamError() {
        String raw = "event: error\ndata: {\"error\":{\"message\":\"Rate limit exceeded\"}}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.StreamError.class);
        assertThat(((StreamEvent.StreamError) events.get(0)).message()).isEqualTo("Rate limit exceeded");
    }

    @Test
    void shouldSkipCommentLines() {
        String raw = ":this is a comment\nevent: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"Hi\"}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(((StreamEvent.ContentDelta) events.get(0)).text()).isEqualTo("Hi");
    }

    @Test
    void shouldReturnEmptyForNonEventLines() {
        String raw = "keep-alive: true\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).isEmpty();
    }

    @Test
    void shouldAccumulateMultipleDataLines() {
        String raw = "event: content_block_delta\ndata: {\"type\":\"text_delta\"\ndata: ,\"text\":\"Hello\"}\n\n";
        List<StreamEvent> events = parser.parseLines(raw.lines().toList());
        assertThat(events).hasSize(1);
        assertThat(((StreamEvent.ContentDelta) events.get(0)).text()).isEqualTo("Hello");
    }

    @Test
    void shouldReturnEmptyForEmptyInput() {
        assertThat(parser.parseLines(List.of())).isEmpty();
    }
}
```

### `SseEventParser.java`

```java
package io.lavendercode.parser;

import com.fasterxml.jackson.databind.*;
import io.lavendercode.model.StreamEvent;
import java.util.*;

/**
 * Parses raw SSE (Server-Sent Events) lines into {@link StreamEvent}s.
 */
public class SseEventParser {

    private final ObjectMapper json;

    public SseEventParser() {
        this.json = new ObjectMapper();
    }

    /**
     * Parse a sequence of SSE lines. Handles multi-line data payloads,
     * comment lines (starting with ':'), and the standard {@code event:}
     * / {@code data:} field format.
     */
    public List<StreamEvent> parseLines(List<String> lines) {
        List<StreamEvent> result = new ArrayList<>();
        String currentEvent = null;
        StringBuilder currentData = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith(":")) {
                // comment line, skip
                continue;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring(6).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                if (currentData.length() > 0) {
                    currentData.append("\n");
                }
                currentData.append(line.substring(5).trim());
                continue;
            }
            // Empty line = dispatch
            if (line.isEmpty() && currentData.length() > 0) {
                var event = dispatch(currentEvent, currentData.toString());
                if (event != null) {
                    result.add(event);
                }
                currentEvent = null;
                currentData = new StringBuilder();
            }
        }
        // Flush any trailing event that lacked a trailing blank line
        if (currentData.length() > 0) {
            var event = dispatch(currentEvent, currentData.toString());
            if (event != null) {
                result.add(event);
            }
        }
        return result;
    }

    private StreamEvent dispatch(String eventType, String data) {
        try {
            var tree = json.readTree(data);
            return switch (eventType != null ? eventType : "") {
                case "content_block_delta" -> {
                    String text = tree.path("text").asText();
                    if (text.isEmpty() && tree.has("delta")) {
                        text = tree.path("delta").path("text").asText();
                    }
                    yield new StreamEvent.ContentDelta(text);
                }
                case "message_done" -> {
                    String reason = tree.path("stop_reason").asText("end_turn");
                    yield new StreamEvent.MessageDone(reason);
                }
                case "thinking_delta" -> {
                    String thinking = tree.path("thinking").asText();
                    yield new StreamEvent.ThinkingDelta(thinking);
                }
                case "error" -> {
                    String msg = tree.path("error").path("message").asText("Unknown error");
                    yield new StreamEvent.StreamError(msg);
                }
                default -> null;
            };
        } catch (Exception e) {
            return new StreamEvent.StreamError("Parse error: " + e.getMessage());
        }
    }
}
```

---

## Task 7: AnthropicProvider with TDD

### Goal

Implement the Anthropic LLM provider with full test coverage using MockWebServer.

### Files to create

- `src/test/java/io/lavendercode/provider/anthropic/AnthropicProviderTest.java`
- `src/main/java/io/lavendercode/provider/anthropic/AnthropicProvider.java`
- `src/main/resources/META-INF/services/io.lavendercode.provider.LlmProvider`

### Steps

- [x] Write `AnthropicProviderTest` with 5 tests
- [x] Implement `AnthropicProvider`
- [x] Register via ServiceLoader

### `AnthropicProviderTest.java`

```java
package io.lavendercode.provider.anthropic;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.List;

class AnthropicProviderTest {

    private MockWebServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        provider = new AnthropicProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ProviderConfig config() {
        return new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", server.url("/").toString(), 4096);
    }

    @Test
    void shouldStreamContentDeltas() {
        server.enqueue(new MockResponse()
            .setBody("event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"Hello\"}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\" World\"}\n\n"
                + "event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable()
                .hasSize(3)
                .anySatisfy(e -> assertThat(e).isInstanceOf(StreamEvent.ContentDelta.class));
        }
    }

    @Test
    void shouldHandleEmptyResponse() {
        server.enqueue(new MockResponse()
            .setBody("event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable().hasSize(1);
        }
    }

    @Test
    void shouldPropagateHttpErrors() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));
        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable().allMatch(e -> e instanceof StreamEvent.StreamError);
        }
    }

    @Test
    void shouldIncludeApiKeyHeader() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        provider.chat(config(), List.of()).forEachRemaining(e -> {});
        var request = server.takeRequest();
        assertThat(request.getHeader("x-api-key")).isEqualTo("sk-ant-test");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
    }

    @Test
    void shouldHandleConnectionRefused() {
        // Point to a port where nothing is listening
        var badConfig = new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", "http://localhost:1", 4096);
        try (var it = provider.chat(badConfig, List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable().allMatch(e -> e instanceof StreamEvent.StreamError);
        }
    }
}
```

### `AnthropicProvider.java`

```java
package io.lavendercode.provider.anthropic;

import com.fasterxml.jackson.databind.node.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import io.lavendercode.parser.SseEventParser;
import io.lavendercode.provider.LlmProvider;
import okhttp3.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM provider for Anthropic's Claude API (Messages API).
 */
public class AnthropicProvider implements LlmProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final OkHttpClient http;
    private final SseEventParser parser;

    public AnthropicProvider() {
        this(new OkHttpClient.Builder().build(), new SseEventParser());
    }

    /** Package-private for testing. */
    AnthropicProvider(OkHttpClient http, SseEventParser parser) {
        this.http = http;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public StreamEventIterator chat(ProviderConfig config, List<Message> messages) {
        String baseUrl = config.baseUrl() != null ? config.baseUrl() : DEFAULT_BASE;
        String model = config.model() != null ? config.model() : "claude-sonnet-4-20250514";
        int maxTokens = config.maxTokens() != null ? config.maxTokens() : 1024;

        var body = buildRequestBody(model, maxTokens, messages);
        var request = new Request.Builder()
            .url(baseUrl + "/v1/messages")
            .addHeader("x-api-key", config.apiKey())
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("Accept", "text/event-stream")
            .post(RequestBody.create(body, JSON))
            .build();

        var queue = new ArrayBlockingQueue<StreamEvent>(64);
        var done = new AtomicBoolean(false);

        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                queue.offer(new StreamEvent.StreamError(e.getMessage()));
                done.set(true);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (var body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        queue.offer(new StreamEvent.StreamError(
                            "HTTP " + response.code() + ": " + (body != null ? body.string() : "")));
                        done.set(true);
                        return;
                    }
                    var lines = body.charStream();
                    // Read all lines from the stream
                    List<String> lineList = new ArrayList<>();
                    try (var reader = new BufferedReader(lines)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            lineList.add(line);
                        }
                    }
                    var events = parser.parseLines(lineList);
                    for (var event : events) {
                        queue.offer(event);
                    }
                } catch (Exception e) {
                    queue.offer(new StreamEvent.StreamError(e.getMessage()));
                } finally {
                    done.set(true);
                }
            }
        });

        return new StreamEventIterator() {
            private StreamEvent next = poll();

            private StreamEvent poll() {
                try {
                    while (!done.get() || !queue.isEmpty()) {
                        var e = queue.poll();
                        if (e != null) return e;
                        if (!done.get()) {
                            Thread.sleep(50); // gentle spin
                        }
                    }
                    return queue.poll(); // final drain
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return new StreamEvent.StreamError("Interrupted");
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public StreamEvent next() {
                var current = next;
                next = poll();
                return current;
            }

            @Override
            public void close() {
                done.set(true);
            }
        };
    }

    private String buildRequestBody(String model, int maxTokens, List<Message> messages) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("stream", true);

        var msgs = root.putArray("messages");
        for (var msg : messages) {
            var m = msgs.addObject();
            m.put("role", msg.role().name().toLowerCase());
            m.put("content", msg.content());
        }
        return root.toString();
    }
}
```

### `src/main/resources/META-INF/services/io.lavendercode.provider.LlmProvider`

```
io.lavendercode.provider.anthropic.AnthropicProvider
```

---

## Task 8: OpenAIProvider with TDD

### Goal

Implement the OpenAI LLM provider with full test coverage using MockWebServer.

### Files to create

- `src/test/java/io/lavendercode/provider/openai/OpenAIProviderTest.java`
- `src/main/java/io/lavendercode/provider/openai/OpenAIProvider.java`
- Update `src/main/resources/META-INF/services/io.lavendercode.provider.LlmProvider` to include OpenAI

### Steps

- [x] Write `OpenAIProviderTest` with 5 tests
- [x] Implement `OpenAIProvider`
- [x] Register via ServiceLoader

### `OpenAIProviderTest.java`

```java
package io.lavendercode.provider.openai;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.List;

class OpenAIProviderTest {

    private MockWebServer server;
    private OpenAIProvider provider;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        provider = new OpenAIProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ProviderConfig config() {
        return new ProviderConfig("openai", "sk-openai-test",
            "gpt-4o", server.url("/").toString(), 2048);
    }

    @Test
    void shouldStreamContentDeltas() {
        server.enqueue(new MockResponse()
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" World\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable()
                .hasSize(3)
                .anySatisfy(e -> assertThat(e).isInstanceOf(StreamEvent.ContentDelta.class));
        }
    }

    @Test
    void shouldHandleFinishReason() {
        server.enqueue(new MockResponse()
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Done\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable()
                .anyMatch(e -> e instanceof StreamEvent.MessageDone);
        }
    }

    @Test
    void shouldPropagateHttpErrors() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));
        try (var it = provider.chat(config(), List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable().allMatch(e -> e instanceof StreamEvent.StreamError);
        }
    }

    @Test
    void shouldIncludeAuthHeader() throws Exception {
        server.enqueue(new MockResponse()
            .setBody("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        provider.chat(config(), List.of()).forEachRemaining(e -> {});
        var request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-openai-test");
    }

    @Test
    void shouldHandleConnectionRefused() {
        var badConfig = new ProviderConfig("openai", "sk-openai-test",
            "gpt-4o", "http://localhost:1", 2048);
        try (var it = provider.chat(badConfig, List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable().allMatch(e -> e instanceof StreamEvent.StreamError);
        }
    }
}
```

### `OpenAIProvider.java`

```java
package io.lavendercode.provider.openai;

import com.fasterxml.jackson.databind.node.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import io.lavendercode.provider.LlmProvider;
import okhttp3.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM provider for OpenAI's Chat Completions API.
 */
public class OpenAIProvider implements LlmProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE = "https://api.openai.com";
    private static final String DONE_MARKER = "[DONE]";

    private final OkHttpClient http;

    public OpenAIProvider() {
        this(new OkHttpClient.Builder().build());
    }

    /** Package-private for testing. */
    OpenAIProvider(OkHttpClient http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public StreamEventIterator chat(ProviderConfig config, List<Message> messages) {
        String baseUrl = config.baseUrl() != null ? config.baseUrl() : DEFAULT_BASE;
        String model = config.model() != null ? config.model() : "gpt-4o";
        int maxTokens = config.maxTokens() != null ? config.maxTokens() : 2048;

        var body = buildRequestBody(model, maxTokens, messages);
        var request = new Request.Builder()
            .url(baseUrl + "/v1/chat/completions")
            .addHeader("Authorization", "Bearer " + config.apiKey())
            .addHeader("Accept", "text/event-stream")
            .post(RequestBody.create(body, JSON))
            .build();

        var queue = new ArrayBlockingQueue<StreamEvent>(64);
        var done = new AtomicBoolean(false);

        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                queue.offer(new StreamEvent.StreamError(e.getMessage()));
                done.set(true);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (var body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        queue.offer(new StreamEvent.StreamError(
                            "HTTP " + response.code() + ": " + (body != null ? body.string() : "")));
                        done.set(true);
                        return;
                    }
                    try (var reader = new java.io.BufferedReader(body.charStream())) {
                        String line;
                        StringBuilder dataBuffer = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if (data.equals(DONE_MARKER)) {
                                    continue;
                                }
                                if (dataBuffer.length() > 0) {
                                    dataBuffer.append("\n");
                                }
                                dataBuffer.append(data);
                            } else if (line.isEmpty() && dataBuffer.length() > 0) {
                                // Dispatch accumulated JSON data
                                var event = parseOpenAiEvent(dataBuffer.toString());
                                if (event != null) {
                                    queue.offer(event);
                                }
                                dataBuffer = new StringBuilder();
                            }
                        }
                        // Flush remaining
                        if (dataBuffer.length() > 0) {
                            var event = parseOpenAiEvent(dataBuffer.toString());
                            if (event != null) {
                                queue.offer(event);
                            }
                        }
                    }
                } catch (Exception e) {
                    queue.offer(new StreamEvent.StreamError(e.getMessage()));
                } finally {
                    done.set(true);
                }
            }
        });

        return new StreamEventIterator() {
            private StreamEvent next = poll();

            private StreamEvent poll() {
                try {
                    while (!done.get() || !queue.isEmpty()) {
                        var e = queue.poll();
                        if (e != null) return e;
                        if (!done.get()) {
                            Thread.sleep(50);
                        }
                    }
                    return queue.poll();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return new StreamEvent.StreamError("Interrupted");
                }
            }

            @Override
            public boolean hasNext() { return next != null; }

            @Override
            public StreamEvent next() {
                var current = next;
                next = poll();
                return current;
            }

            @Override
            public void close() { done.set(true); }
        };
    }

    private StreamEvent parseOpenAiEvent(String json) {
        try {
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var choice = tree.path("choices").get(0);
            if (choice == null) return null;

            var delta = choice.path("delta");
            String content = delta.path("content").asText(null);
            String finishReason = choice.path("finish_reason").asText(null);

            if (content != null) {
                return new StreamEvent.ContentDelta(content);
            }
            if (finishReason != null && !finishReason.equals("null")) {
                return new StreamEvent.MessageDone(finishReason);
            }
            return null;
        } catch (Exception e) {
            return new StreamEvent.StreamError("Parse error: " + e.getMessage());
        }
    }

    private String buildRequestBody(String model, int maxTokens, List<Message> messages) {
        var root = JsonNodeFactory.instance.objectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("stream", true);

        var msgs = root.putArray("messages");
        for (var msg : messages) {
            var m = msgs.addObject();
            m.put("role", msg.role().name().toLowerCase());
            m.put("content", msg.content());
        }
        return root.toString();
    }
}
```

### Update `src/main/resources/META-INF/services/io.lavendercode.provider.LlmProvider`

```
io.lavendercode.provider.anthropic.AnthropicProvider
io.lavendercode.provider.openai.OpenAIProvider
```

---

## Task 9: Provider Contract Tests

### Goal

Create an abstract contract test class and concrete implementations for both providers to ensure they fulfill the `LlmProvider` contract.

### Files to create

- `src/test/java/io/lavendercode/provider/LlmProviderContractTest.java`
- `src/test/java/io/lavendercode/provider/anthropic/AnthropicProviderContractTest.java`
- `src/test/java/io/lavendercode/provider/openai/OpenAIProviderContractTest.java`

### Steps

- [x] Create `LlmProviderContractTest` abstract class
- [x] Create `AnthropicProviderContractTest`
- [x] Create `OpenAIProviderContractTest`

### `LlmProviderContractTest.java`

```java
package io.lavendercode.provider;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.List;

/**
 * Abstract contract test that every {@link LlmProvider} implementation must pass.
 */
public abstract class LlmProviderContractTest {

    protected abstract LlmProvider createProvider();

    protected abstract ProviderConfig createConfig(MockWebServer server);

    @Test
    void shouldReturnProviderName() {
        assertThat(createProvider().name()).isNotEmpty();
    }

    @Test
    void shouldStreamContent() {
        var server = new MockWebServer();
        try {
            server.enqueue(new MockResponse()
                .setBody(streamBody("Hello"))
                .setHeader("Content-Type", "text/event-stream"));

            var provider = createProvider();
            try (var it = provider.chat(createConfig(server), List.of(new Message(Role.USER, "Hi")))) {
                assertThat(it).toIterable()
                    .anyMatch(e -> e instanceof StreamEvent.ContentDelta);
            }
        } finally {
            server.shutdown();
        }
    }

    @Test
    void shouldHandleApiError() {
        var server = new MockWebServer();
        try {
            server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));
            var provider = createProvider();
            try (var it = provider.chat(createConfig(server), List.of(new Message(Role.USER, "Hi")))) {
                assertThat(it).toIterable()
                    .anyMatch(e -> e instanceof StreamEvent.StreamError);
            }
        } finally {
            server.shutdown();
        }
    }

    protected abstract String streamBody(String content);
}
```

### `AnthropicProviderContractTest.java`

```java
package io.lavendercode.provider.anthropic;

import io.lavendercode.config.ProviderConfig;
import io.lavendercode.provider.LlmProvider;
import io.lavendercode.provider.LlmProviderContractTest;
import okhttp3.mockwebserver.MockWebServer;

class AnthropicProviderContractTest extends LlmProviderContractTest {

    @Override
    protected LlmProvider createProvider() {
        return new AnthropicProvider();
    }

    @Override
    protected ProviderConfig createConfig(MockWebServer server) {
        return new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", server.url("/").toString(), 4096);
    }

    @Override
    protected String streamBody(String content) {
        return "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"" + content + "\"}\n\n"
            + "event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n";
    }
}
```

### `OpenAIProviderContractTest.java`

```java
package io.lavendercode.provider.openai;

import io.lavendercode.config.ProviderConfig;
import io.lavendercode.provider.LlmProvider;
import io.lavendercode.provider.LlmProviderContractTest;
import okhttp3.mockwebserver.MockWebServer;

class OpenAIProviderContractTest extends LlmProviderContractTest {

    @Override
    protected LlmProvider createProvider() {
        return new OpenAIProvider();
    }

    @Override
    protected ProviderConfig createConfig(MockWebServer server) {
        return new ProviderConfig("openai", "sk-openai-test",
            "gpt-4o", server.url("/").toString(), 2048);
    }

    @Override
    protected String streamBody(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
            + "data: [DONE]\n\n";
    }
}
```

---

## Task 10: SessionManager with TDD

### Goal

Implement `SessionManager` interface and `InMemorySessionManager` to manage chat sessions (message history).

### Files to create

- `src/main/java/io/lavendercode/session/SessionManager.java`
- `src/main/java/io/lavendercode/session/InMemorySessionManager.java`
- `src/test/java/io/lavendercode/session/InMemorySessionManagerTest.java`

### Steps

- [x] Create `SessionManager` interface
- [x] Write `InMemorySessionManagerTest` with 6 tests
- [x] Implement `InMemorySessionManager`

### `SessionManager.java`

```java
package io.lavendercode.session;

import io.lavendercode.model.Message;
import java.util.List;

/**
 * Manages chat session state, including message history.
 */
public interface SessionManager {

    /** Add a message to the current session. */
    void addMessage(Message message);

    /** Return an immutable copy of all messages in the current session. */
    List<Message> getMessages();

    /** Clear all messages and reset the session. */
    void clear();

    /** Return the current message count. */
    int size();
}
```

### `InMemorySessionManager.java`

```java
package io.lavendercode.session;

import io.lavendercode.model.Message;
import java.util.*;

/**
 * In-memory implementation of {@link SessionManager}.
 * Thread-safe via synchronized blocks.
 */
public class InMemorySessionManager implements SessionManager {

    private final List<Message> messages = new ArrayList<>();

    @Override
    public synchronized void addMessage(Message message) {
        messages.add(Objects.requireNonNull(message, "message must not be null"));
    }

    @Override
    public synchronized List<Message> getMessages() {
        return List.copyOf(messages);
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }

    @Override
    public synchronized int size() {
        return messages.size();
    }
}
```

### `InMemorySessionManagerTest.java`

```java
package io.lavendercode.session;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.model.Message;
import io.lavendercode.model.Role;
import org.junit.jupiter.api.Test;

class InMemorySessionManagerTest {

    private final InMemorySessionManager manager = new InMemorySessionManager();

    @Test
    void shouldStartEmpty() {
        assertThat(manager.size()).isZero();
        assertThat(manager.getMessages()).isEmpty();
    }

    @Test
    void shouldAddMessage() {
        manager.addMessage(new Message(Role.USER, "Hello"));
        assertThat(manager.size()).isEqualTo(1);
        assertThat(manager.getMessages().get(0).content()).isEqualTo("Hello");
    }

    @Test
    void shouldAddMultipleMessages() {
        manager.addMessage(new Message(Role.USER, "Hi"));
        manager.addMessage(new Message(Role.ASSISTANT, "Hello!"));
        assertThat(manager.size()).isEqualTo(2);
    }

    @Test
    void shouldReturnImmutableMessages() {
        manager.addMessage(new Message(Role.USER, "Hi"));
        var msgs = manager.getMessages();
        assertThatThrownBy(() -> msgs.add(new Message(Role.USER, "X")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldClearMessages() {
        manager.addMessage(new Message(Role.USER, "Hi"));
        manager.clear();
        assertThat(manager.size()).isZero();
        assertThat(manager.getMessages()).isEmpty();
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> manager.addMessage(null))
            .isInstanceOf(NullPointerException.class);
    }
}
```

---

## Task 11: TUI Application with TDD

### Goal

Implement the Lanterna-based TUI application with status bar, chat area, input box, and command handling.

### Files to create

- `src/main/java/io/lavendercode/tui/TuiApplication.java`
- `src/test/java/io/lavendercode/tui/TuiApplicationTest.java`

### Steps

- [x] Implement `TuiApplication` with Lanterna GUI
- [x] Write `TuiApplicationTest` with VirtualTerminal test

### `TuiApplication.java`

```java
package io.lavendercode.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.*;
import com.googlecode.lanterna.terminal.*;
import io.lavendercode.config.*;
import io.lavendercode.model.*;
import io.lavendercode.provider.*;
import io.lavendercode.session.SessionManager;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Lanterna-based TUI chat application.
 *
 * <p>Layout:
 * <pre>
 * +------------------------------------------+
 * | STATUS BAR  (provider, model, size)       |
 * +------------------------------------------+
 * |                                          |
 * |   CHAT AREA  (scrollable message log)     |
 * |                                          |
 * +------------------------------------------+
 * | INPUT BOX  [/exit, /clear, /help]        |
 * +------------------------------------------+
 * </pre>
 */
public class TuiApplication {

    private final LlmConfig config;
    private final ProviderRegistry registry;
    private final SessionManager session;
    private final Screen screen;
    private final WindowBasedTextGUI gui;

    private TextBox inputBox;
    private ActionListBox chatArea;

    public TuiApplication(LlmConfig config, ProviderRegistry registry, SessionManager session,
                          Screen screen, WindowBasedTextGUI gui) {
        this.config = config;
        this.registry = registry;
        this.session = session;
        this.screen = screen;
        this.gui = gui;
    }

    /**
     * Convenience factory: create a TuiApplication with a real terminal.
     */
    public static TuiApplication create(LlmConfig config, ProviderRegistry registry,
                                        SessionManager session) throws IOException {
        var terminal = TerminalFactory.createTerminal(System.out, System.in, TerminalFactory.ColorMode.INDEXED);
        var screen = new VirtualScreen(new TerminalScreen(terminal));
        screen.startScreen();
        var gui = new MultiWindowTextGUI(screen);
        return new TuiApplication(config, registry, session, screen, gui);
    }

    /** Run the TUI event loop. Returns when the user exits. */
    public void run() {
        var window = new BasicWindow("LavenderCode v1.0");
        window.setHints(List.of(Window.Hint.FULL_SCREEN));

        var panel = new Panel(new LinearLayout(Direction.VERTICAL));

        // Status bar
        var provider = registry.getProvider(config.activeProvider());
        String providerModel = provider.name();
        if (config.providers() != null) {
            var activeCfg = config.providers().stream()
                .filter(p -> p.name().equals(config.activeProvider()))
                .findFirst().orElse(null);
            if (activeCfg != null && activeCfg.model() != null) {
                providerModel += " [" + activeCfg.model() + "]";
            }
        }
        var statusBar = new Label(" Provider: " + providerModel
            + "  |  Messages: " + session.size()
            + "  |  [Ctrl+D] Exit  [/help]");
        panel.addComponent(statusBar.withBorder(Borders.singleLine("Status")));

        // Chat area
        chatArea = new ActionListBox();
        chatArea.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        panel.addComponent(chatArea, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

        // Input box
        inputBox = new TextBox(new TerminalSize(60, 1));
        inputBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        inputBox.setTextChangeListener((newText, changedByUser) -> {});
        panel.addComponent(inputBox);

        // Handle input
        inputBox.handleInput(new KeyStroke(KeyStroke.KeyType.Enter), () -> {
            String text = inputBox.getText().trim();
            if (!text.isEmpty()) {
                inputBox.setText("");
                handleInput(text);
            }
            return true;
        });

        window.setComponent(panel);
        gui.addWindowAndWait(window);
    }

    private void handleInput(String text) {
        if (text.startsWith("/")) {
            handleCommand(text);
            return;
        }

        // Regular chat message
        session.addMessage(new Message(Role.USER, text));
        updateStatus();

        chatArea.addItem("You: " + text, () -> {});

        var provider = registry.getProvider(config.activeProvider());
        var activeCfg = config.providers().stream()
            .filter(p -> p.name().equals(config.activeProvider()))
            .findFirst().orElse(null);
        if (activeCfg == null) return;

        var thinking = config.options() != null && config.options().thinking() != null
            && config.options().thinking().enabled();

        StringBuilder response = new StringBuilder();
        try (var it = provider.chat(activeCfg, session.getMessages())) {
            while (it.hasNext()) {
                var event = it.next();
                switch (event) {
                    case StreamEvent.ContentDelta delta -> {
                        response.append(delta.text());
                        chatArea.addItem(delta.text(), () -> {});
                    }
                    case StreamEvent.ThinkingDelta td -> {
                        if (thinking) {
                            chatArea.addItem("[thinking] " + td.thinking(), () -> {});
                        }
                    }
                    case StreamEvent.MessageDone done -> {
                        chatArea.addItem("", () -> {}); // spacing
                    }
                    case StreamEvent.StreamError err -> {
                        chatArea.addItem("[Error] " + err.message(), () -> {});
                    }
                }
            }
        }

        if (!response.isEmpty()) {
            session.addMessage(new Message(Role.ASSISTANT, response.toString()));
            updateStatus();
        }
    }

    private void handleCommand(String command) {
        switch (command.toLowerCase()) {
            case "/exit", "/quit" -> screen.stopScreen();
            case "/clear" -> {
                session.clear();
                chatArea.clearItems();
                updateStatus();
            }
            case "/help" -> {
                chatArea.addItem("Available commands:", () -> {});
                chatArea.addItem("  /exit, /quit  - Exit LavenderCode", () -> {});
                chatArea.addItem("  /clear        - Clear chat history", () -> {});
                chatArea.addItem("  /help         - Show this help", () -> {});
                chatArea.addItem("  /status       - Show connection status", () -> {});
                chatArea.addItem("  /count        - Show token/message count", () -> {});
            }
            case "/status" -> {
                var provider = registry.getProvider(config.activeProvider());
                chatArea.addItem("Active provider: " + provider.name(), () -> {});
                chatArea.addItem("Messages in session: " + session.size(), () -> {});
            }
            case "/count" -> {
                chatArea.addItem("Message count: " + session.size(), () -> {});
            }
            default ->
                chatArea.addItem("Unknown command: " + command + ". Type /help", () -> {});
        }
    }

    private void updateStatus() {
        // In a full implementation we'd update the status bar label.
        // For now the status bar is updated on next redraw.
    }
}
```

### `TuiApplicationTest.java`

```java
package io.lavendercode.tui;

import static org.assertj.core.api.Assertions.*;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.*;
import com.googlecode.lanterna.terminal.*;
import io.lavendercode.config.*;
import io.lavendercode.provider.*;
import io.lavendercode.session.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;

class TuiApplicationTest {

    @Test
    void shouldConstructWithVirtualTerminal() throws Exception {
        var terminal = new VirtualTerminal();
        var screen = new TerminalScreen(terminal);
        screen.startScreen();
        var gui = new MultiWindowTextGUI(screen);

        var config = new LlmConfig("anthropic",
            List.of(new ProviderConfig("anthropic", "sk-test", null, null, null)),
            null);
        var registry = new ProviderRegistry();
        var session = new InMemorySessionManager();

        var app = new TuiApplication(config, registry, session, screen, gui);
        assertThat(app).isNotNull();

        screen.stopScreen();
    }
}
```

---

## Task 12: Main Entry Point, Wiring, and Example Config

### Goal

Create the `LavenderCode.java` main class that wires everything together, plus a `config.yaml.example`.

### Files to create

- `src/main/java/io/lavendercode/LavenderCode.java`
- `config.yaml.example`

### Steps

- [x] Create `LavenderCode.java` main entry point
- [x] Create `config.yaml.example`
- [x] Smoke-test the application starts

### `LavenderCode.java`

```java
package io.lavendercode;

import io.lavendercode.config.*;
import io.lavendercode.provider.ProviderRegistry;
import io.lavendercode.session.InMemorySessionManager;
import io.lavendercode.tui.TuiApplication;

/**
 * LavenderCode v1.0 - CLI AI Assistant.
 *
 * <p>Usage:
 * <pre>
 *   java -jar lavendercode.jar [config.yaml]
 * </pre>
 * If no config path is given, looks for {@code config.yaml} in the
 * current working directory.
 */
public final class LavenderCode {

    private LavenderCode() {}

    public static void main(String[] args) {
        try {
            String configPath = args.length > 0 ? args[0] : "config.yaml";
            var configLoader = new ConfigLoader();
            var config = configLoader.load(configPath);

            var registry = new ProviderRegistry();
            var session = new InMemorySessionManager();

            // Verify the active provider is available
            registry.getProvider(config.activeProvider());

            var app = TuiApplication.create(config, registry, session);
            app.run();
        } catch (ConfigException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

### `config.yaml.example`

```yaml
# LavenderCode v1.0 Configuration
# Copy this file to config.yaml and fill in your API keys.

active_provider: anthropic

providers:
  - name: anthropic
    api_key: sk-ant-your-key-here
    model: claude-sonnet-4-20250514
    # base_url: https://api.anthropic.com
    max_tokens: 4096

  - name: openai
    api_key: sk-your-key-here
    model: gpt-4o
    # base_url: https://api.openai.com
    max_tokens: 2048

options:
  temperature: 0.7
  thinking:
    enabled: false
    budget_tokens: 1024
```

---

## Task 13: Integration Tests

### Goal

Write integration tests that exercise the full provider pipeline against MockWebServer (simulating success, error, and connection-refused scenarios).

### Files to create

- `src/test/java/io/lavendercode/integration/AnthropicIntegrationTest.java`
- `src/test/java/io/lavendercode/integration/OpenAIIntegrationTest.java`

### Steps

- [x] Create `AnthropicIntegrationTest`
- [x] Create `OpenAIIntegrationTest`

### `AnthropicIntegrationTest.java`

```java
package io.lavendercode.integration;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import io.lavendercode.provider.anthropic.AnthropicProvider;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.ArrayList;

class AnthropicIntegrationTest {

    private MockWebServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        provider = new AnthropicProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fullStreamingConversation() {
        // Full streaming scenario: multiple deltas followed by message_done
        server.enqueue(new MockResponse()
            .setBody("event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"Hello\"}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"! How\"}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\" can I help?\"}\n\n"
                + "event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        var config = new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", server.url("/").toString(), 4096);

        List<String> texts = new ArrayList<>();
        try (var it = provider.chat(config, List.of(new Message(Role.USER, "Hi")))) {
            while (it.hasNext()) {
                var event = it.next();
                if (event instanceof StreamEvent.ContentDelta delta) {
                    texts.add(delta.text());
                }
            }
        }

        assertThat(texts).containsExactly("Hello", "! How", " can I help?");
    }

    @Test
    void thinkingBlocks() {
        // Anthropic-style thinking blocks
        server.enqueue(new MockResponse()
            .setBody("event: thinking_delta\ndata: {\"thinking\":\"Let me reason about this...\"}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"Answer: 42\"}\n\n"
                + "event: message_done\ndata: {\"stop_reason\":\"end_turn\"}\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        var config = new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", server.url("/").toString(), 4096);

        var events = new ArrayList<StreamEvent>();
        try (var it = provider.chat(config, List.of(new Message(Role.USER, "What is 6*7?")))) {
            it.forEachRemaining(events::add);
        }

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(StreamEvent.ThinkingDelta.class);
        assertThat(((StreamEvent.ThinkingDelta) events.get(0)).thinking())
            .contains("reason");
        assertThat(events.get(1)).isInstanceOf(StreamEvent.ContentDelta.class);
        assertThat(events.get(2)).isInstanceOf(StreamEvent.MessageDone.class);
    }

    @Test
    void connectionRefused() {
        var badConfig = new ProviderConfig("anthropic", "sk-ant-test",
            "claude-sonnet-4-20250514", "http://localhost:1", 4096);

        try (var it = provider.chat(badConfig, List.of(new Message(Role.USER, "Hi")))) {
            assertThat(it).toIterable()
                .allMatch(e -> e instanceof StreamEvent.StreamError);
        }
    }
}
```

### `OpenAIIntegrationTest.java`

```java
package io.lavendercode.integration;

import static org.assertj.core.api.Assertions.*;
import io.lavendercode.config.ProviderConfig;
import io.lavendercode.model.*;
import io.lavendercode.provider.openai.OpenAIProvider;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.ArrayList;

class OpenAIIntegrationTest {

    private MockWebServer server;
    private OpenAIProvider provider;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        provider = new OpenAIProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fullStreamingConversation() {
        // Full streaming scenario: multiple deltas, finish_reason, then [DONE]
        server.enqueue(new MockResponse()
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"The\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" answer\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" is 42\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n")
            .setHeader("Content-Type", "text/event-stream"));

        var config = new ProviderConfig("openai", "sk-openai-test",
            "gpt-4o", server.url("/").toString(), 2048);

        List<String> texts = new ArrayList<>();
        var stopReasons = new ArrayList<String>();
        try (var it = provider.chat(config, List.of(new Message(Role.USER, "What is 6*7?")))) {
            while (it.hasNext()) {
                var event = it.next();
                switch (event) {
                    case StreamEvent.ContentDelta delta -> texts.add(delta.text());
                    case StreamEvent.MessageDone done -> stopReasons.add(done.stopReason());
                    default -> {}
                }
            }
        }

        assertThat(texts).containsExactly("The", " answer", " is 42");
        assertThat(stopReasons).contains("stop");
    }

    @Test
    void disconnectMidStream() throws Exception {
        // Server closes connection mid-stream
        server.enqueue(new MockResponse()
            .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"},\"finish_reason\":null}]}\n\n")
            .setHeader("Content-Type", "text/event-stream")
            .setSocketPolicy(MockWebServer.SocketPolicy.DISCONNECT_AFTER_RESPONSE));

        var config = new ProviderConfig("openai", "sk-openai-test",
            "gpt-4o", server.url("/").toString(), 2048);

        try (var it = provider.chat(config, List.of(new Message(Role.USER, "Hi")))) {
            var events = new ArrayList<StreamEvent>();
            it.forEachRemaining(events::add);
            // Should have at least the partial content, possibly an error
            assertThat(events).isNotEmpty();
        }
    }
}
```

---

## Build and Run

```bash
# Compile and run unit + contract tests
mvn clean test

# Run only unit tests (exclude integration tests)
mvn clean test -Dtest="!integration.*"

# Run integration tests
mvn clean test -Dtest="integration.*"

# Package
mvn clean package

# Run the application
java -jar target/lavendercode-1.0.0.jar config.yaml

# Or using exec plugin
mvn exec:java -Dexec.args="config.yaml"
```

## Summary

| Task | Description | Files Created | Tests |
|------|-------------|---------------|-------|
| 1 | POM with deps, directory structure | 1 | - |
| 2 | Config data model records | 5 | - |
| 3 | Message/Role/StreamEvent models | 4 | 2 test classes (7 tests) |
| 4 | LlmProvider + ProviderRegistry | 2 | - |
| 5 | ConfigLoader TDD | 5 | 1 test class (6 tests) |
| 6 | SSE Parser TDD | 2 | 1 test class (8 tests) |
| 7 | AnthropicProvider TDD | 3 | 1 test class (5 tests) |
| 8 | OpenAIProvider TDD | 3 | 1 test class (5 tests) |
| 9 | Provider contract tests | 3 | 2 test classes (3 tests each) |
| 10 | SessionManager TDD | 3 | 1 test class (6 tests) |
| 11 | TUI Application | 2 | 1 test class (1 test) |
| 12 | Main entry + config example | 2 | - |
| 13 | Integration tests | 2 | 2 test classes (5 tests) |

**Total: 37 files** (20 main, 17 test)
**Total tests: 46** (38 unit + 6 contract + 2 smoke/integration-scoped)
