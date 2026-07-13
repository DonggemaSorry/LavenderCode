package com.lavendercode.core.memory;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.provider.StreamEvent;
import com.lavendercode.core.provider.StreamEventIterator;
import com.lavendercode.core.prompt.PromptContext;
import com.lavendercode.core.tool.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MemoryServiceUpdateTest {

    @TempDir
    Path projectRoot;

    @TempDir
    Path userHome;

    @Test
    void appliesCreateUpdateDeleteFromLlmJson() throws Exception {
        MemoryService service = new MemoryService(projectRoot, userHome);
        String createJson = """
            ```json
            [
              {
                "action": "create",
                "level": "project",
                "type": "project_knowledge",
                "title": "Build command",
                "slug": "build_command",
                "filename": "project_knowledge_build_command.md",
                "content": "Use mvn test for verification.\\nMore detail."
              }
            ]
            ```
            """;

        List<MemoryAction> create = service.parseActions(createJson);
        service.applyActions(create);

        Path note = projectRoot.resolve(".lavendercode/memory/project_knowledge_build_command.md");
        Path index = projectRoot.resolve(".lavendercode/memory/MEMORY.md");
        String createdNote = Files.readString(note);
        assertThat(createdNote).contains(
            "type: project_knowledge",
            "title: Build command",
            "created: ",
            "updated: ",
            "Use mvn test for verification.");
        String originalCreated = frontmatterValue(createdNote, "created");
        String originalUpdated = frontmatterValue(createdNote, "updated");
        assertThat(originalCreated).isNotBlank();
        assertThat(originalUpdated).isNotBlank();
        assertThat(Files.readString(index))
            .contains("- [project_knowledge] Build command — Use mvn test for verification.");

        Thread.sleep(10);
        service.applyActions(service.parseActions("""
            [
              {
                "action": "update",
                "level": "project",
                "type": "project_knowledge",
                "title": "Build command",
                "slug": "build_command",
                "filename": "project_knowledge_build_command.md",
                "content": "Use mvn -q test before completion."
              }
            ]
            """));

        String updatedNote = Files.readString(note);
        assertThat(updatedNote).contains("Use mvn -q test before completion.");
        assertThat(frontmatterValue(updatedNote, "created")).isEqualTo(originalCreated);
        assertThat(frontmatterValue(updatedNote, "updated")).isNotEqualTo(originalUpdated);
        assertThat(Files.readString(index))
            .doesNotContain("Use mvn test for verification.")
            .contains("- [project_knowledge] Build command — Use mvn -q test before completion.");

        service.applyActions(service.parseActions("""
            [
              {
                "action": "delete",
                "level": "project",
                "type": "project_knowledge",
                "title": "Build command",
                "slug": "build_command",
                "filename": "project_knowledge_build_command.md",
                "content": ""
              }
            ]
            """));

        assertThat(note).doesNotExist();
        assertThat(Files.readString(index)).doesNotContain("Build command");
    }

    @Test
    void shouldUpdateForKeywordsAndEveryFiveTurns() {
        MemoryService service = new MemoryService(projectRoot, userHome);

        assertThat(service.shouldUpdate(0, "remember this")).isTrue();
        assertThat(service.shouldUpdate(1, "请记住我的偏好")).isTrue();
        assertThat(service.shouldUpdate(2, "这个要加入记忆")).isTrue();
        assertThat(service.shouldUpdate(3, "别忘了这个约束")).isTrue();
        assertThat(service.shouldUpdate(4, "plain message")).isFalse();
        assertThat(service.shouldUpdate(5, "plain message")).isTrue();
        assertThat(service.shouldUpdate(0, "plain message")).isFalse();
    }

    @Test
    void updateFailureIsSwallowed() {
        MemoryService service = new MemoryService(projectRoot, userHome);
        LlmProvider provider = new ThrowingProvider();

        assertThatCode(() -> service.maybeUpdateAsync(
            provider, config(), List.of(new Message(Role.USER, "remember this")), 1, "remember this"))
            .doesNotThrowAnyException();
    }

    @Test
    void updateFailureIsLoggedAtWarningLevel() throws Exception {
        MemoryService service = new MemoryService(projectRoot, userHome);
        LlmProvider provider = new ThrowingProvider();
        Logger logger = Logger.getLogger(MemoryService.class.getName());
        CountDownLatch logged = new CountDownLatch(1);
        AtomicReference<LogRecord> record = new AtomicReference<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                record.set(logRecord);
                logged.countDown();
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            service.maybeUpdateAsync(
                provider, config(), List.of(new Message(Role.USER, "remember this")), 1, "remember this");

            assertThat(logged.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(record.get().getLevel()).isEqualTo(Level.WARNING);
            assertThat(record.get().getMessage()).contains("boom");
            assertThat(record.get().getThrown()).isInstanceOf(IllegalStateException.class);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void maybeUpdateAsyncReturnsImmediatelyAndCallsProviderWithoutTools() throws Exception {
        MemoryService service = new MemoryService(projectRoot, userHome);
        BlockingProvider provider = new BlockingProvider("""
            [
              {
                "action": "create",
                "level": "user",
                "type": "user_preference",
                "title": "Shell",
                "slug": "shell",
                "filename": "user_preference_shell.md",
                "content": "Prefers concise output."
              }
            ]
            """);

        long started = System.nanoTime();
        service.maybeUpdateAsync(
            provider, config(), List.of(new Message(Role.USER, "hello")), 5, "hello");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(Duration.ofMillis(200));
        assertThat(provider.entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(provider.toolCount).isEqualTo(0);
        assertThat(provider.promptContext.stablePrompt()).contains("JSON array only");

        provider.release.countDown();
        awaitFile(userHome.resolve(".lavendercode/memory/user_preference_shell.md"));
        awaitCurrentIndex(service, "Shell");
        assertThat(service.currentIndex()).contains("Shell");
    }

    private static void awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + path);
    }

    private static void awaitCurrentIndex(MemoryService service, String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (service.currentIndex().contains(text)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for current index to contain " + text);
    }

    private static String frontmatterValue(String note, String key) {
        return note.lines()
            .filter(line -> line.startsWith(key + ": "))
            .map(line -> line.substring((key + ": ").length()))
            .findFirst()
            .orElse("");
    }

    private static LlmConfig config() {
        return new LlmConfig(
            List.of(ProviderConfig.of("test", "openai-compatible", "gpt-4", "http://localhost", "key", null)),
            null);
    }

    private static final class ThrowingProvider implements LlmProvider {
        @Override
        public String protocol() {
            return "openai-compatible";
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
            throw new IllegalStateException("boom");
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                              List<ToolDefinition> toolDefs,
                                              PromptContext promptContext) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class BlockingProvider implements LlmProvider {
        private final String text;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile int toolCount = -1;
        private volatile PromptContext promptContext;

        private BlockingProvider(String text) {
            this.text = text;
        }

        @Override
        public String protocol() {
            return "openai-compatible";
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config) {
            throw new UnsupportedOperationException("prompt context required");
        }

        @Override
        public StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                              List<ToolDefinition> toolDefs,
                                              PromptContext promptContext) {
            this.toolCount = toolDefs.size();
            this.promptContext = promptContext;
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return iterator(text);
        }
    }

    private static StreamEventIterator iterator(String text) {
        return new StreamEventIterator() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < 2;
            }

            @Override
            public StreamEvent next() {
                index++;
                if (index == 1) {
                    return new StreamEvent.ContentDelta(text);
                }
                return new StreamEvent.StreamComplete();
            }

            @Override
            public void close() {
            }
        };
    }
}
