# Terminal Chat Interface Redesign — Implementation Plan (TDD)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **All tasks follow strict TDD: write test → red → implementation → green → commit.**

**Goal:** Replace Lanterna gui2 TUI with a JLine3 native terminal chat experience — streaming typewriter output, code block highlighting, scrollable message area, configurable themes.

**Architecture:** Four-thread event-driven design: InputThread (JLine3 LineReader) → NetworkThread (orchestration) → I/O pool (SSE consumption) → RenderThread (terminal drawing). Communication via thread-safe queues (InputQueue, RenderQueue). Theme management via semantic StyleCatalog + Theme record.

**Tech Stack:** Java 17, JLine 3.26.3, OkHttp 4.12, Jackson 2.17, JUnit 5, Mockito 5, AssertJ 3.26

**Note:** Current `StreamEventIterator` is buffered (full response pre-read). The `RequestContext.call` field is nullable for MVP. True streaming cancellation is deferred.

---

## Phase 1: Infrastructure & Data Types (Tasks 1-4)

### Task 1: Add JLine3 dependency + Create unix package directory

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/lavendercode/chat/terminal/` (directory)

- [ ] **Step 1: Add JLine3 dependency to pom.xml**

Insert after Lanterna dependency block (line 63):

```xml
<!-- JLine3 Native Terminal -->
<dependency>
    <groupId>org.jline</groupId>
    <artifactId>jline</artifactId>
    <version>3.26.3</version>
</dependency>
```

- [ ] **Step 2: Verify dependency downloads**

Run: `mvn dependency:resolve -q -f pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Create package directories**

```bash
mkdir -p src/main/java/com/lavendercode/chat/terminal
mkdir -p src/test/java/com/lavendercode/chat/terminal
```

- [ ] **Step 4: Run all existing tests to verify no regressions**

Run: `mvn test -q`
Expected: All existing tests pass

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
feat: add JLine3 3.26.3 dependency for native terminal support

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Create InputEvent (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/InputEventTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/InputEvent.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `InputEventTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InputEventTest {

    @Test
    void shouldCreateSendMessage() {
        var event = new InputEvent.SendMessage("Hello");
        assertThat(event.text()).isEqualTo("Hello");
        assertThat(event).isInstanceOf(InputEvent.class);
    }

    @Test
    void shouldCreateExecuteCommand() {
        var event = new InputEvent.ExecuteCommand(CommandType.HELP, "");
        assertThat(event.type()).isEqualTo(CommandType.HELP);
        assertThat(event.args()).isEmpty();
    }

    @Test
    void shouldCreateShutdown() {
        assertThat(new InputEvent.Shutdown()).isNotNull();
    }

    @Test
    void shouldSupportPatternMatching() {
        InputEvent send    = new InputEvent.SendMessage("x");
        InputEvent cmd     = new InputEvent.ExecuteCommand(CommandType.EXIT, "x");
        InputEvent shutdown = new InputEvent.Shutdown();

        String result = switch (send) {
            case InputEvent.SendMessage(var t)  -> "msg:" + t;
            case InputEvent.ExecuteCommand(var c, var a) -> "cmd:" + c;
            case InputEvent.Shutdown()          -> "shutdown";
        };
        assertThat(result).isEqualTo("msg:x");
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=InputEventTest -q`
Expected: **COMPILATION ERROR** — `InputEvent` not found

- [ ] **Step 3: Write minimal implementation to make tests pass (GREEN)**

Write `InputEvent.java`:

```java
package com.lavendercode.chat.terminal;

public sealed interface InputEvent {

    record SendMessage(String text) implements InputEvent {}

    record ExecuteCommand(CommandType type, String args) implements InputEvent {}

    record Shutdown() implements InputEvent {}

    enum CommandType {
        EXIT, QUIT, CLEAR, HELP, THEME, CANCEL, SCROLL
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=InputEventTest -q`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/InputEvent.java \
        src/test/java/com/lavendercode/chat/terminal/InputEventTest.java
git commit -m "$(cat <<'EOF'
feat: add InputEvent sealed interface with CommandType enum (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Create RenderEvent (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/RenderEventTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/RenderEvent.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `RenderEventTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderEventTest {

    // ---- AppendToMessage ----
    @Test
    void appendToMessageShouldStoreText() {
        var e = new RenderEvent.AppendToMessage("delta");
        assertThat(e.text()).isEqualTo("delta");
    }

    // ---- FinalizeMessage ----
    @Test
    void finalizeMessageShouldInstantiate() {
        assertThat(new RenderEvent.FinalizeMessage()).isNotNull();
    }

    // ---- AddUserMessage ----
    @Test
    void addUserMessageShouldStoreText() {
        var e = new RenderEvent.AddUserMessage("hello");
        assertThat(e.text()).isEqualTo("hello");
    }

    // ---- AddSystemMessage ----
    @Test
    void addSystemMessageShouldStoreText() {
        var e = new RenderEvent.AddSystemMessage("err");
        assertThat(e.text()).isEqualTo("err");
    }

    // ---- ThinkDelta ----
    @Test
    void thinkDeltaShouldStoreText() {
        var e = new RenderEvent.ThinkDelta("reasoning");
        assertThat(e.text()).isEqualTo("reasoning");
    }

    // ---- ScrollTo ----
    @Test
    void scrollToShouldRejectNegativeIndex() {
        assertThatThrownBy(() -> new RenderEvent.ScrollTo(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scrollToShouldAcceptZero() {
        var e = new RenderEvent.ScrollTo(0);
        assertThat(e.lineIndex()).isZero();
    }

    // ---- ScrollDelta ----
    @Test
    void scrollDeltaShouldStoreOffset() {
        var e = new RenderEvent.ScrollDelta(5);
        assertThat(e.offset()).isEqualTo(5);
    }

    // ---- ScrollAutoReset ----
    @Test
    void scrollAutoResetShouldInstantiate() {
        assertThat(new RenderEvent.ScrollAutoReset()).isNotNull();
    }

    // ---- ClearChat ----
    @Test
    void clearChatShouldInstantiate() {
        assertThat(new RenderEvent.ClearChat()).isNotNull();
    }

    // ---- WindowResize ----
    @Test
    void windowResizeShouldRejectInvalidDimensions() {
        assertThatThrownBy(() -> new RenderEvent.WindowResize(0, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void windowResizeShouldStoreDimensions() {
        var e = new RenderEvent.WindowResize(80, 24);
        assertThat(e.cols()).isEqualTo(80);
        assertThat(e.rows()).isEqualTo(24);
    }

    // ---- ThemeChange ----
    @Test
    void themeChangeShouldStoreTheme() {
        var theme = Theme.dark();
        var e = new RenderEvent.ThemeChange(theme);
        assertThat(e.theme()).isEqualTo(theme);
    }

    // ---- StatusUpdate ----
    @Test
    void statusUpdateShouldStoreFields() {
        var e = new RenderEvent.StatusUpdate("claude", 1234, true);
        assertThat(e.model()).isEqualTo("claude");
        assertThat(e.tokenCount()).isEqualTo(1234);
        assertThat(e.isEstimating()).isTrue();
    }

    // ---- RefreshAll ----
    @Test
    void refreshAllShouldInstantiate() {
        assertThat(new RenderEvent.RefreshAll()).isNotNull();
    }

    // ---- Shutdown ----
    @Test
    void shutdownShouldInstantiate() {
        assertThat(new RenderEvent.Shutdown()).isNotNull();
    }

    // ---- Sealed hierarchy ----
    @Test
    void shouldBeSealedInterface() {
        assertThat(new RenderEvent.AppendToMessage("x")).isInstanceOf(RenderEvent.class);
        assertThat(new RenderEvent.FinalizeMessage()).isInstanceOf(RenderEvent.class);
        assertThat(new RenderEvent.Shutdown()).isInstanceOf(RenderEvent.class);
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=RenderEventTest -q`
Expected: **COMPILATION ERROR** — `RenderEvent` not found

- [ ] **Step 3: Write minimal implementation (GREEN)**

Write `RenderEvent.java`:

```java
package com.lavendercode.chat.terminal;

import java.util.Objects;

public sealed interface RenderEvent
    permits RenderEvent.AppendToMessage,
            RenderEvent.FinalizeMessage,
            RenderEvent.AddUserMessage,
            RenderEvent.AddSystemMessage,
            RenderEvent.ThinkDelta,
            RenderEvent.ScrollTo,
            RenderEvent.ScrollDelta,
            RenderEvent.ScrollAutoReset,
            RenderEvent.ClearChat,
            RenderEvent.WindowResize,
            RenderEvent.ThemeChange,
            RenderEvent.StatusUpdate,
            RenderEvent.RefreshAll,
            RenderEvent.Shutdown {

    record AppendToMessage(String text) implements RenderEvent {
        public AppendToMessage {
            Objects.requireNonNull(text);
        }
    }

    record FinalizeMessage() implements RenderEvent {}

    record AddUserMessage(String text) implements RenderEvent {
        public AddUserMessage { Objects.requireNonNull(text); }
    }

    record AddSystemMessage(String text) implements RenderEvent {
        public AddSystemMessage { Objects.requireNonNull(text); }
    }

    record ThinkDelta(String text) implements RenderEvent {
        public ThinkDelta { Objects.requireNonNull(text); }
    }

    record ScrollTo(int lineIndex) implements RenderEvent {
        public ScrollTo {
            if (lineIndex < 0) throw new IllegalArgumentException("lineIndex must be >= 0");
        }
    }

    record ScrollDelta(int offset) implements RenderEvent {}

    record ScrollAutoReset() implements RenderEvent {}

    record ClearChat() implements RenderEvent {}

    record WindowResize(int cols, int rows) implements RenderEvent {
        public WindowResize {
            if (cols < 1) throw new IllegalArgumentException("cols must be >= 1");
            if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
        }
    }

    record ThemeChange(Theme theme) implements RenderEvent {
        public ThemeChange { Objects.requireNonNull(theme); }
    }

    record StatusUpdate(String model, int tokenCount, boolean isEstimating) implements RenderEvent {
        public StatusUpdate { Objects.requireNonNull(model); }
    }

    record RefreshAll() implements RenderEvent {}

    record Shutdown() implements RenderEvent {}
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=RenderEventTest -q`
Expected: Tests run: 17, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/RenderEvent.java \
        src/test/java/com/lavendercode/chat/terminal/RenderEventTest.java
git commit -m "$(cat <<'EOF'
feat: add RenderEvent sealed interface with 14 event types (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Create DeltaEvent + StyleCatalog + RenderedLine + Theme (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/DataTypesTest.java` (combined test)
- Create: `src/main/java/com/lavendercode/chat/terminal/DeltaEvent.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/StyleCatalog.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/RenderedLine.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/Theme.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `DataTypesTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;
import org.jline.utils.AttributedString;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataTypesTest {

    // ==== DeltaEvent ====

    @Test
    void deltaContentShouldStoreText() {
        var e = new DeltaEvent.Content("hello");
        assertThat(e.text()).isEqualTo("hello");
    }

    @Test
    void deltaThinkingShouldStoreText() {
        var e = new DeltaEvent.Thinking("reasoning");
        assertThat(e.text()).isEqualTo("reasoning");
    }

    @Test
    void deltaCompleteShouldInstantiate() {
        assertThat(new DeltaEvent.Complete()).isNotNull();
    }

    @Test
    void deltaErrorShouldStoreMessageAndCode() {
        var e = new DeltaEvent.Error("timeout", 503);
        assertThat(e.message()).isEqualTo("timeout");
        assertThat(e.statusCode()).isEqualTo(503);
    }

    @Test
    void deltaUsageShouldStoreTokenCounts() {
        var e = new DeltaEvent.Usage(100, 50);
        assertThat(e.inputTokens()).isEqualTo(100);
        assertThat(e.outputTokens()).isEqualTo(50);
    }

    // ==== StyleCatalog ====

    @Test
    void styleCatalogShouldHave12Entries() {
        assertThat(StyleCatalog.values()).hasSize(12);
    }

    @Test
    void styleCatalogShouldContainCodeBlock() {
        assertThat(StyleCatalog.valueOf("CODE_BLOCK")).isNotNull();
    }

    // ==== RenderedLine ====

    @Test
    void renderedLineShouldStoreSegments() {
        var seg = new AttributedString("text");
        var line = new RenderedLine(seg);
        assertThat(line.segments()).hasSize(1);
    }

    // ==== Theme ====

    @Test
    void darkThemeShouldHaveAllStyleEntries() {
        Theme dark = Theme.dark();
        assertThat(dark.name()).isEqualTo("dark");
        for (StyleCatalog key : StyleCatalog.values()) {
            assertThat(dark.styles()).as("missing key: " + key).containsKey(key);
        }
    }

    @Test
    void lightThemeShouldHaveAllStyleEntries() {
        Theme light = Theme.light();
        assertThat(light.name()).isEqualTo("light");
        for (StyleCatalog key : StyleCatalog.values()) {
            assertThat(light.styles()).as("missing key: " + key).containsKey(key);
        }
    }

    @Test
    void themeApplyShouldReturnAttributedString() {
        Theme dark = Theme.dark();
        var result = dark.apply(StyleCatalog.PROMPT, "> ");
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("> ");
    }

    @Test
    void themeStylesShouldBeUnmodifiable() {
        Theme dark = Theme.dark();
        assertThatThrownBy(() -> dark.styles().put(StyleCatalog.PROMPT, null))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=DataTypesTest -q`
Expected: **COMPILATION ERROR** — `DeltaEvent`, `StyleCatalog`, `RenderedLine`, `Theme` not found

- [ ] **Step 3: Write implementations (GREEN)**

Write `DeltaEvent.java`:

```java
package com.lavendercode.chat.terminal;

import java.util.Objects;

public sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.Thinking,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {

    record Content(String text) implements DeltaEvent {
        public Content { Objects.requireNonNull(text); }
    }

    record Thinking(String text) implements DeltaEvent {
        public Thinking { Objects.requireNonNull(text); }
    }

    record Complete() implements DeltaEvent {}

    record Error(String message, int statusCode) implements DeltaEvent {
        public Error { Objects.requireNonNull(message); }
    }

    record Usage(int inputTokens, int outputTokens) implements DeltaEvent {}
}
```

Write `StyleCatalog.java`:

```java
package com.lavendercode.chat.terminal;

public enum StyleCatalog {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    ASSISTANT_BORDER,
    SYSTEM_MESSAGE,
    CODE_BLOCK,
    THINKING_TEXT,
    THINKING_LABEL,
    STATUS_BAR,
    SCROLLBAR_TRACK,
    SCROLLBAR_THUMB,
    PROMPT,
    INPUT_TEXT
}
```

Write `RenderedLine.java`:

```java
package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import java.util.List;
import java.util.Objects;

public record RenderedLine(List<AttributedString> segments) {
    public RenderedLine {
        Objects.requireNonNull(segments);
    }

    public RenderedLine(AttributedString segment) {
        this(List.of(segment));
    }
}
```

Write `Theme.java`:

```java
package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Colors;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record Theme(String name, Map<StyleCatalog, AttributedStyle> styles) {

    public Theme {
        Objects.requireNonNull(name);
        Objects.requireNonNull(styles);
    }

    public AttributedString apply(StyleCatalog key, String text) {
        AttributedStyle style = styles.getOrDefault(key, AttributedStyle.DEFAULT);
        return new AttributedString(text, style);
    }

    public static Theme dark() {
        Map<StyleCatalog, AttributedStyle> map = new EnumMap<>(StyleCatalog.class);
        map.put(StyleCatalog.USER_MESSAGE,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("00ffff")));
        map.put(StyleCatalog.ASSISTANT_MESSAGE, AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffffff")));
        map.put(StyleCatalog.ASSISTANT_BORDER,  AttributedStyle.DEFAULT.foreground(Colors.rgbColor("4444ff")));
        map.put(StyleCatalog.SYSTEM_MESSAGE,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffff00")).italic());
        map.put(StyleCatalog.CODE_BLOCK,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffffff")).background(Colors.rgbColor("444444")));
        map.put(StyleCatalog.THINKING_TEXT,     AttributedStyle.DEFAULT.foreground(Colors.rgbColor("888888")).italic());
        map.put(StyleCatalog.THINKING_LABEL,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ff00ff")));
        map.put(StyleCatalog.STATUS_BAR,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("000000")).background(Colors.rgbColor("00ffff")));
        map.put(StyleCatalog.SCROLLBAR_TRACK,   AttributedStyle.DEFAULT.foreground(Colors.rgbColor("555555")));
        map.put(StyleCatalog.SCROLLBAR_THUMB,   AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffffff")).bold());
        map.put(StyleCatalog.PROMPT,            AttributedStyle.DEFAULT.foreground(Colors.rgbColor("00ff00")).bold());
        map.put(StyleCatalog.INPUT_TEXT,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffffff")));
        return new Theme("dark", Collections.unmodifiableMap(map));
    }

    public static Theme light() {
        Map<StyleCatalog, AttributedStyle> map = new EnumMap<>(StyleCatalog.class);
        map.put(StyleCatalog.USER_MESSAGE,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("0055aa")));
        map.put(StyleCatalog.ASSISTANT_MESSAGE, AttributedStyle.DEFAULT.foreground(Colors.rgbColor("000000")));
        map.put(StyleCatalog.ASSISTANT_BORDER,  AttributedStyle.DEFAULT.foreground(Colors.rgbColor("0000cc")));
        map.put(StyleCatalog.SYSTEM_MESSAGE,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("aa5500")).italic());
        map.put(StyleCatalog.CODE_BLOCK,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("000000")).background(Colors.rgbColor("dddddd")));
        map.put(StyleCatalog.THINKING_TEXT,     AttributedStyle.DEFAULT.foreground(Colors.rgbColor("777777")).italic());
        map.put(StyleCatalog.THINKING_LABEL,    AttributedStyle.DEFAULT.foreground(Colors.rgbColor("aa00aa")));
        map.put(StyleCatalog.STATUS_BAR,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("ffffff")).background(Colors.rgbColor("0055aa")));
        map.put(StyleCatalog.SCROLLBAR_TRACK,   AttributedStyle.DEFAULT.foreground(Colors.rgbColor("cccccc")));
        map.put(StyleCatalog.SCROLLBAR_THUMB,   AttributedStyle.DEFAULT.foreground(Colors.rgbColor("000000")).bold());
        map.put(StyleCatalog.PROMPT,            AttributedStyle.DEFAULT.foreground(Colors.rgbColor("00aa00")).bold());
        map.put(StyleCatalog.INPUT_TEXT,        AttributedStyle.DEFAULT.foreground(Colors.rgbColor("000000")));
        return new Theme("light", Collections.unmodifiableMap(map));
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=DataTypesTest -q`
Expected: Tests run: 12, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/DeltaEvent.java \
        src/main/java/com/lavendercode/chat/terminal/StyleCatalog.java \
        src/main/java/com/lavendercode/chat/terminal/RenderedLine.java \
        src/main/java/com/lavendercode/chat/terminal/Theme.java \
        src/test/java/com/lavendercode/chat/terminal/DataTypesTest.java
git commit -m "$(cat <<'EOF'
feat: add DeltaEvent, StyleCatalog, RenderedLine, Theme with dark/light (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: Core Logic (Tasks 5-6)

### Task 5: Create MessageBlock (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/MessageBlockTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/MessageBlock.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `MessageBlockTest.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBlockTest {

    @Test
    void newBlockShouldHaveZeroLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        assertThat(block.lineCount()).isZero();
    }

    @Test
    void appendSingleLineShouldWrap() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.append("Hello", 80);
        assertThat(added).isEqualTo(1);
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void appendOverWidthShouldWrapToMultipleLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        // 10 chars at width 5 = 2 lines
        int added = block.append("1234567890", 5);
        assertThat(added).isEqualTo(2);
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void appendMultipleTimesShouldAccumulateCorrectly() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Hello", 80);
        // "Hello World" = 11 chars, still 1 line at width 80
        int added = block.append(" World", 80);
        assertThat(added).isZero();
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void newlinesShouldCreateMultipleLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.append("Line1\nLine2", 80);
        assertThat(added).isEqualTo(2);
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void codeBlockFencesShouldBeDetected() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Before\n```python\ncode\n```\nAfter", 80);
        // 5 logical lines: Before, ```, code, ```, After
        assertThat(block.lineCount()).isEqualTo(5);
    }

    @Test
    void appendThinkingShouldCreateThinkingLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        int added = block.appendThinking("Let me think...", 80);
        assertThat(added).isEqualTo(1);
        assertThat(block.lineCount()).isEqualTo(1);
    }

    @Test
    void reflowShouldHandleWidthChange() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("1234567890", 10); // 1 line at width 10
        assertThat(block.lineCount()).isEqualTo(1);
        block.reflow(5); // should become 2 lines at width 5
        assertThat(block.lineCount()).isEqualTo(2);
    }

    @Test
    void markCompleteShouldSetFlag() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        assertThat(block.isComplete()).isFalse();
        block.markComplete();
        assertThat(block.isComplete()).isTrue();
    }

    @Test
    void shouldReturnAllLines() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("A\nB", 80);
        assertThat(block.allLines()).hasSize(2);
    }

    @Test
    void thinkingContentMixedShouldRetainOrder() {
        MessageBlock block = new MessageBlock(Role.ASSISTANT);
        block.append("Answer:", 80);
        block.appendThinking("Let me think...", 80);
        block.append(" The result is 42.", 80);
        assertThat(block.lineCount()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=MessageBlockTest -q`
Expected: **COMPILATION ERROR**

- [ ] **Step 3: Write implementation (GREEN)**

Write `MessageBlock.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.utils.AttributedString;
import org.jline.utils.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageBlock {

    private final UUID id;
    private final Role role;
    private boolean isComplete;
    private boolean inCodeBlock;
    private final List<Segment> segments;

    public MessageBlock(Role role) {
        this.id = UUID.randomUUID();
        this.role = role;
        this.isComplete = false;
        this.inCodeBlock = false;
        this.segments = new ArrayList<>();
    }

    public boolean isComplete() { return isComplete; }
    public void markComplete() { this.isComplete = true; }

    public int lineCount() {
        return segments.stream().mapToInt(s -> s.lines.size()).sum();
    }

    public int append(String text, int terminalWidth) {
        int oldCount = lineCount();
        ensureLastContentSegment();
        ContentSegment last = (ContentSegment) segments.get(segments.size() - 1);
        last.rawText.append(text);
        last.lines.clear();
        wrapAndColor(last.rawText.toString(), terminalWidth, last.lines);
        return lineCount() - oldCount;
    }

    public int appendThinking(String text, int terminalWidth) {
        int oldCount = lineCount();
        ThinkingSegment last = findLastThinkingSegment();
        if (last == null) {
            last = new ThinkingSegment();
            segments.add(last);
        }
        last.rawText.append(text);
        last.lines.clear();
        wrapAsThinking(last.rawText.toString(), terminalWidth, last.lines);
        return lineCount() - oldCount;
    }

    public List<RenderedLine> allLines() {
        List<RenderedLine> result = new ArrayList<>();
        for (Segment seg : segments) {
            result.addAll(seg.lines);
        }
        return result;
    }

    public void reflow(int terminalWidth) {
        for (Segment seg : segments) {
            seg.lines.clear();
            if (seg instanceof ContentSegment cs) {
                boolean saved = inCodeBlock;
                // Need to reset inCodeBlock for reflow
                inCodeBlock = false;
                wrapAndColor(cs.rawText.toString(), terminalWidth, cs.lines);
            } else if (seg instanceof ThinkingSegment ts) {
                wrapAsThinking(ts.rawText.toString(), terminalWidth, ts.lines);
            }
        }
    }

    // --- Private helpers ---

    private void ensureLastContentSegment() {
        if (segments.isEmpty() || !(segments.get(segments.size() - 1) instanceof ContentSegment)) {
            segments.add(new ContentSegment());
        }
    }

    private ThinkingSegment findLastThinkingSegment() {
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i) instanceof ThinkingSegment ts) return ts;
        }
        return null;
    }

    private void wrapAndColor(String raw, int width, List<RenderedLine> out) {
        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n') {
                flushLineToOutput(currentLine.toString(), width, out);
                currentLine.setLength(0);
            } else {
                currentLine.append(c);
            }
        }
        if (currentLine.length() > 0) {
            flushLineToOutput(currentLine.toString(), width, out);
        }
    }

    private void flushLineToOutput(String line, int width, List<RenderedLine> out) {
        if (line.startsWith("```")) {
            // fence marker line
            out.add(new RenderedLine(new AttributedString(line,
                AttributedStyle.DEFAULT.foreground(Colors.rgbColor("888888")))));
            inCodeBlock = !inCodeBlock;
            return;
        }

        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + width, line.length());
            String seg = line.substring(start, end);
            if (inCodeBlock) {
                out.add(new RenderedLine(new AttributedString(seg,
                    AttributedStyle.DEFAULT.background(Colors.rgbColor("444444")))));
            } else {
                out.add(new RenderedLine(new AttributedString(seg)));
            }
            start = end;
        }
    }

    private void wrapAsThinking(String raw, int width, List<RenderedLine> out) {
        for (String line : raw.split("\n", -1)) {
            int start = 0;
            while (start < line.length()) {
                int end = Math.min(start + width, line.length());
                out.add(new RenderedLine(new AttributedString(line.substring(start, end),
                    AttributedStyle.DEFAULT.italic())));
                start = end;
            }
        }
    }

    // --- Inner types ---

    private static abstract sealed class Segment
        permits ContentSegment, ThinkingSegment {
        final List<RenderedLine> lines = new ArrayList<>();
    }

    private static final class ContentSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }

    private static final class ThinkingSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=MessageBlockTest -q`
Expected: Tests run: 11, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/MessageBlock.java \
        src/test/java/com/lavendercode/chat/terminal/MessageBlockTest.java
git commit -m "$(cat <<'EOF'
feat: add MessageBlock with line-wrapping and code-fence detection (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Create DeltaBuffer (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/DeltaBufferTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/DeltaBuffer.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `DeltaBufferTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaBufferTest {

    private ScheduledExecutorService scheduler;
    private BlockingQueue<RenderEvent> renderQueue;
    private DeltaBuffer buffer;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        renderQueue = new LinkedBlockingQueue<>();
        buffer = new DeltaBuffer(scheduler, renderQueue);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    // ---- flush via timer ----

    @Test
    void singleDeltaShouldFlushViaTimer() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "hello", 0));

        RenderEvent event = renderQueue.poll(200, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) event).text()).isEqualTo("hello");
    }

    @Test
    void adjacentContentDeltasShouldMerge() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "Hel", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "lo", 0));

        RenderEvent event = renderQueue.poll(200, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) event).text()).isEqualTo("Hello");
    }

    // ---- forceFlush ----

    @Test
    void forceFlushShouldDrainImmediately() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "fast", 0));
        buffer.forceFlush();

        RenderEvent event = renderQueue.poll(10, TimeUnit.MILLISECONDS);
        assertThat(event).isNotNull();
        assertThat(event).isInstanceOf(RenderEvent.AppendToMessage.class);
    }

    @Test
    void completeShouldFlushFirstThenFinalize() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "text", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.STREAM_COMPLETE, "", 0));
        buffer.forceFlush();

        RenderEvent e1 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e2 = renderQueue.poll(100, TimeUnit.MILLISECONDS);

        assertThat(e1).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(e2).isInstanceOf(RenderEvent.FinalizeMessage.class);
    }

    // ---- ordering ----

    @Test
    void contentThinkContentShouldRetainArrivalOrder() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "A", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.THINK_DELTA, "think", 0));
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, "B", 0));
        buffer.forceFlush();

        RenderEvent e1 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e2 = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        RenderEvent e3 = renderQueue.poll(100, TimeUnit.MILLISECONDS);

        assertThat(e1).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) e1).text()).isEqualTo("A");
        assertThat(e2).isInstanceOf(RenderEvent.ThinkDelta.class);
        assertThat(((RenderEvent.ThinkDelta) e2).text()).isEqualTo("think");
        assertThat(e3).isInstanceOf(RenderEvent.AppendToMessage.class);
        assertThat(((RenderEvent.AppendToMessage) e3).text()).isEqualTo("B");
    }

    // ---- concurrency ----

    @Test
    void forceFlushShouldHandleConcurrentAppends() throws Exception {
        for (int i = 0; i < 100; i++) {
            buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, String.valueOf(i), 0));
        }
        buffer.forceFlush();

        // All 100 same-type deltas merge into 1 event
        int count = 0;
        while (renderQueue.poll(50, TimeUnit.MILLISECONDS) != null) count++;
        assertThat(count).isEqualTo(1);
    }

    // ---- error type ----

    @Test
    void streamErrorShouldBecomeAddSystemMessage() throws Exception {
        buffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.STREAM_ERROR, "fail", 500));
        buffer.forceFlush();

        RenderEvent event = renderQueue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddSystemMessage.class);
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("fail");
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=DeltaBufferTest -q`
Expected: **COMPILATION ERROR** — `DeltaBuffer` not found

- [ ] **Step 3: Write implementation (GREEN)**

Write `DeltaBuffer.java`:

```java
package com.lavendercode.chat.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe delta buffer that batches incoming events on a 50ms timer.
 * forceFlush() drains immediately. Uses snapshot-then-clear for data safety.
 */
public class DeltaBuffer {

    public record BufferedEvent(Type type, String text, int statusCode) {

        public enum Type {
            CONTENT_DELTA, THINK_DELTA,
            STREAM_COMPLETE, STREAM_ERROR,
            USER_MESSAGE, SYSTEM_MESSAGE
        }

        public BufferedEvent(Type type, String text, int statusCode) {
            this.type = type;
            this.text = text != null ? text : "";
            this.statusCode = statusCode;
        }

        public RenderEvent toRenderEvent() {
            return switch (type) {
                case STREAM_COMPLETE -> new RenderEvent.FinalizeMessage();
                case STREAM_ERROR    -> new RenderEvent.AddSystemMessage("[Error] " + text);
                case USER_MESSAGE    -> new RenderEvent.AddUserMessage(text);
                case SYSTEM_MESSAGE  -> new RenderEvent.AddSystemMessage(text);
                default -> throw new IllegalStateException("Cannot convert " + type);
            };
        }
    }

    private final List<BufferedEvent> events = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;
    private volatile boolean flushing;
    private final Object lock = new Object();
    private final ScheduledExecutorService timerScheduler;
    private final BlockingQueue<RenderEvent> renderQueue;

    public DeltaBuffer(ScheduledExecutorService timerScheduler,
                       BlockingQueue<RenderEvent> renderQueue) {
        this.timerScheduler = timerScheduler;
        this.renderQueue = renderQueue;
    }

    public void append(BufferedEvent event) {
        synchronized (lock) {
            events.add(event);
            scheduleIfNeeded();
        }
    }

    public void forceFlush() {
        cancelTimer();
        doFlush();
        while (hasPending()) {
            cancelTimer();
            doFlush();
        }
    }

    // --- private ---

    private void scheduleIfNeeded() {
        if (scheduledFlush == null || scheduledFlush.isDone()) {
            scheduledFlush = timerScheduler.schedule(this::doFlush, 50, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelTimer() {
        synchronized (lock) {
            if (scheduledFlush != null && !scheduledFlush.isDone()) {
                scheduledFlush.cancel(false);
            }
        }
    }

    private boolean hasPending() {
        synchronized (lock) { return !events.isEmpty(); }
    }

    private void doFlush() {
        List<BufferedEvent> snapshot;
        synchronized (lock) {
            if (flushing) return;
            flushing = true;
            snapshot = new ArrayList<>(events);
            events.clear();
        }

        try {
            List<RenderEvent> batch = buildBatch(snapshot);
            for (RenderEvent e : batch) {
                renderQueue.put(e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            flushing = false;
        }
    }

    private List<RenderEvent> buildBatch(List<BufferedEvent> snapshot) {
        List<RenderEvent> result = new ArrayList<>();
        StringBuilder textBuf = new StringBuilder();
        StringBuilder thinkBuf = new StringBuilder();
        BufferedEvent.Type lastType = null;

        for (BufferedEvent e : snapshot) {
            if (e.type != lastType && lastType != null) {
                flushBuffer(result, lastType, textBuf, thinkBuf);
            }
            switch (e.type) {
                case CONTENT_DELTA -> textBuf.append(e.text);
                case THINK_DELTA   -> thinkBuf.append(e.text);
                case STREAM_COMPLETE, STREAM_ERROR,
                     USER_MESSAGE, SYSTEM_MESSAGE -> {
                    flushBuffer(result, lastType, textBuf, thinkBuf);
                    result.add(e.toRenderEvent());
                }
            }
            lastType = e.type;
        }
        flushBuffer(result, lastType, textBuf, thinkBuf);
        return result;
    }

    private void flushBuffer(List<RenderEvent> result, BufferedEvent.Type type,
                             StringBuilder textBuf, StringBuilder thinkBuf) {
        if (type == BufferedEvent.Type.CONTENT_DELTA && textBuf.length() > 0) {
            result.add(new RenderEvent.AppendToMessage(textBuf.toString()));
            textBuf.setLength(0);
        }
        if (type == BufferedEvent.Type.THINK_DELTA && thinkBuf.length() > 0) {
            result.add(new RenderEvent.ThinkDelta(thinkBuf.toString()));
            thinkBuf.setLength(0);
        }
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=DeltaBufferTest -q`
Expected: Tests run: 7, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/DeltaBuffer.java \
        src/test/java/com/lavendercode/chat/terminal/DeltaBufferTest.java
git commit -m "$(cat <<'EOF'
feat: add DeltaBuffer with 50ms batch merging and forceFlush (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: ChatService & Orchestration (Tasks 7-9)

### Task 7: Create ChatService + RequestContext (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/RequestContextTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/RequestContext.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/ChatService.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `RequestContextTest.java`:

```java
package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    @Test
    void shouldStartAsNotCancelled() {
        RequestContext ctx = new RequestContext(null, null);
        assertThat(ctx.isCancelled()).isFalse();
    }

    @Test
    void cancelShouldSetCancelled() {
        RequestContext ctx = new RequestContext(null, null);
        ctx.cancel();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void cancelShouldBeIdempotent() {
        RequestContext ctx = new RequestContext(null, null);
        ctx.cancel();
        ctx.cancel();
        assertThat(ctx.isCancelled()).isTrue();
    }

    @Test
    void shouldAcceptNullCallAndIterator() {
        RequestContext ctx = new RequestContext(null, null);
        assertThat(ctx).isNotNull();
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=RequestContextTest -q`
Expected: **COMPILATION ERROR**

- [ ] **Step 3: Write implementations (GREEN)**

Write `ChatService.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.Message;

import java.util.List;
import java.util.function.Consumer;

public interface ChatService {
    RequestContext submit(LlmProvider provider,
                          List<Message> history,
                          LlmConfig config,
                          Consumer<DeltaEvent> onDelta);

    void cancel(RequestContext ctx);
}
```

Write `RequestContext.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.StreamEventIterator;
import okhttp3.Call;

import java.util.concurrent.atomic.AtomicBoolean;

public class RequestContext {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Call call;
    private final StreamEventIterator iterator;

    /**
     * @param call     OkHttp Call for network cancellation (nullable for buffered iterators)
     * @param iterator the StreamEventIterator to close
     */
    public RequestContext(Call call, StreamEventIterator iterator) {
        this.call = call;
        this.iterator = iterator;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
        if (call != null) {
            call.cancel();
        }
        if (iterator != null) {
            iterator.close();
        }
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=RequestContextTest -q`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/ChatService.java \
        src/main/java/com/lavendercode/chat/terminal/RequestContext.java \
        src/test/java/com/lavendercode/chat/terminal/RequestContextTest.java
git commit -m "$(cat <<'EOF'
feat: add ChatService interface and RequestContext with cancellation (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Create StreamingChatService (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/StreamingChatServiceTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/StreamingChatService.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `StreamingChatServiceTest.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StreamingChatServiceTest {

    @Test
    void shouldConvertStreamEventsToDeltaEvents() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next())
            .thenReturn(new StreamEvent.ContentDelta("hello"))
            .thenReturn(new StreamEvent.ContentDelta(" world"));
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        LlmConfig config = new LlmConfig(
            new ProviderConfig("model", "openai-compatible", "http://localhost", "key", null),
            null
        );

        List<DeltaEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        StreamingChatService service = new StreamingChatService();
        service.submit(provider, List.of(), config, delta -> {
            received.add(delta);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);

        assertThat(received).hasSize(3);
        assertThat(received.get(0)).isInstanceOf(DeltaEvent.Content.class);
        assertThat(((DeltaEvent.Content) received.get(0)).text()).isEqualTo("hello");
        assertThat(received.get(1)).isInstanceOf(DeltaEvent.Content.class);
        assertThat(received.get(2)).isInstanceOf(DeltaEvent.Complete.class);
    }

    @Test
    void shouldConvertErrorEvent() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next())
            .thenReturn(new StreamEvent.StreamError("fail", 500));
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        List<DeltaEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        StreamingChatService service = new StreamingChatService();
        service.submit(provider, List.of(), null, delta -> {
            received.add(delta);
            latch.countDown();
        });

        latch.await(2, TimeUnit.SECONDS);

        assertThat(received.get(0)).isInstanceOf(DeltaEvent.Error.class);
    }

    @Test
    void cancelShouldStopProcessing() throws Exception {
        LlmProvider provider = mock(LlmProvider.class);
        StreamEventIterator iterator = mock(StreamEventIterator.class);
        when(iterator.hasNext()).thenReturn(true);
        when(provider.streamChat(anyList(), any())).thenReturn(iterator);

        CountDownLatch started = new CountDownLatch(1);
        StreamingChatService service = new StreamingChatService();
        RequestContext ctx = service.submit(provider, List.of(), null, delta -> {
            started.countDown();
            try { Thread.sleep(1000); } catch (Exception ignored) {}
        });

        started.await(1, TimeUnit.SECONDS);
        assertThat(ctx.isCancelled()).isFalse();
        service.cancel(ctx);
        assertThat(ctx.isCancelled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=StreamingChatServiceTest -q`
Expected: **COMPILATION ERROR**

- [ ] **Step 3: Write implementation (GREEN)**

Write `StreamingChatService.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class StreamingChatService implements ChatService {

    private final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lavender-io");
        t.setDaemon(true);
        return t;
    });

    @Override
    public RequestContext submit(LlmProvider provider,
                                 List<Message> history,
                                 LlmConfig config,
                                 Consumer<DeltaEvent> onDelta) {
        StreamEventIterator iterator = provider.streamChat(history, config);
        RequestContext ctx = new RequestContext(null, iterator);

        ioPool.submit(() -> {
            try {
                while (iterator.hasNext() && !ctx.isCancelled()) {
                    StreamEvent se = iterator.next();
                    if (ctx.isCancelled()) break;
                    DeltaEvent de = toDeltaEvent(se);
                    if (de != null) {
                        onDelta.accept(de);
                    }
                }
                if (!ctx.isCancelled()) {
                    onDelta.accept(new DeltaEvent.Complete());
                }
            } catch (Exception e) {
                if (!ctx.isCancelled()) {
                    onDelta.accept(new DeltaEvent.Error(e.getMessage(), 0));
                }
            } finally {
                iterator.close();
            }
        });

        return ctx;
    }

    @Override
    public void cancel(RequestContext ctx) {
        ctx.cancel();
    }

    private DeltaEvent toDeltaEvent(StreamEvent se) {
        return switch (se) {
            case StreamEvent.ContentDelta cd  -> new DeltaEvent.Content(cd.text());
            case StreamEvent.ThinkingDelta td -> new DeltaEvent.Thinking(td.text());
            case StreamEvent.StreamComplete sc -> null;
            case StreamEvent.StreamError err  -> new DeltaEvent.Error(err.message(), err.statusCode());
        };
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=StreamingChatServiceTest -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/StreamingChatService.java \
        src/test/java/com/lavendercode/chat/terminal/StreamingChatServiceTest.java
git commit -m "$(cat <<'EOF'
feat: add StreamingChatService — async SSE consumption via ioPool (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Create NetworkOrchestrator (TDD)

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/NetworkOrchestratorTest.java`
- Create: `src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java`

- [ ] **Step 1: Write the failing test (RED)**

Write `NetworkOrchestratorTest.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NetworkOrchestratorTest {

    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private SessionManager sessionManager;
    private LlmProvider provider;
    private ChatService chatService;
    private NetworkOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        inputQueue = new LinkedBlockingQueue<>();
        renderQueue = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        sessionManager = new InMemorySessionManager();
        provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");
        chatService = mock(ChatService.class);
        LlmConfig config = new LlmConfig(
            new ProviderConfig("gpt-4", "openai-compatible", "http://localhost", "key", null), null);

        orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "gpt-4", config, Theme.dark()
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void sendMessageShouldPutAddUserMessage() throws Exception {
        inputQueue.put(new InputEvent.SendMessage("hello"));

        // Run orchestrator in background
        CountDownLatch done = new CountDownLatch(1);
        new Thread(() -> { orchestrator.run(); done.countDown(); }).start();

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddUserMessage.class);
        assertThat(((RenderEvent.AddUserMessage) event).text()).isEqualTo("hello");
    }

    @Test
    void clearCommandShouldPutClearChat() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, ""));
        inputQueue.put(new InputEvent.Shutdown());

        new Thread(orchestrator::run).start();

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.ClearChat.class);
    }

    @Test
    void themeCommandShouldPutThemeChange() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.THEME, "light"));
        inputQueue.put(new InputEvent.Shutdown());

        new Thread(orchestrator::run).start();

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.ThemeChange.class);
        assertThat(((RenderEvent.ThemeChange) event).theme().name()).isEqualTo("light");
    }

    @Test
    void exitCommandShouldPutShutdown() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""));
        inputQueue.put(new InputEvent.Shutdown());

        new Thread(orchestrator::run).start();

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.Shutdown.class);
    }

    @Test
    void helpCommandShouldPutSystemMessage() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, ""));
        inputQueue.put(new InputEvent.Shutdown());

        new Thread(orchestrator::run).start();

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.AddSystemMessage.class);
        assertThat(((RenderEvent.AddSystemMessage) event).text()).contains("/exit");
    }
}
```

- [ ] **Step 2: Run test — verify RED**

Run: `mvn test -Dtest=NetworkOrchestratorTest -q`
Expected: **COMPILATION ERROR**

- [ ] **Step 3: Write implementation (GREEN)**

Write `NetworkOrchestrator.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkOrchestrator {

    private final AtomicReference<RequestContext> currentRequest = new AtomicReference<>();
    private final ChatService chatService;
    private final DeltaBuffer deltaBuffer;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final BlockingQueue<InputEvent> inputQueue;
    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String modelName;
    private final LlmConfig config;
    private Theme theme;

    public NetworkOrchestrator(ChatService chatService, DeltaBuffer deltaBuffer,
                               BlockingQueue<RenderEvent> renderQueue,
                               BlockingQueue<InputEvent> inputQueue,
                               SessionManager sessionManager, LlmProvider provider,
                               String modelName, LlmConfig config, Theme theme) {
        this.chatService = chatService;
        this.deltaBuffer = deltaBuffer;
        this.renderQueue = renderQueue;
        this.inputQueue = inputQueue;
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
    }

    public void run() {
        try {
            while (true) {
                InputEvent event = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                switch (event) {
                    case InputEvent.SendMessage msg -> handleSendMessage(msg);
                    case InputEvent.ExecuteCommand cmd -> handleCommand(cmd);
                    case InputEvent.Shutdown __ -> { handleShutdown(); return; }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSendMessage(InputEvent.SendMessage msg) {
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.AddUserMessage(msg.text()));
        sessionManager.addUserMessage(msg.text());

        var ctxRef = new AtomicReference<RequestContext>();
        try {
            ctxRef.set(chatService.submit(
                provider, sessionManager.getHistory(), config,
                delta -> onDeltaReceived(ctxRef.get(), delta)
            ));
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            currentRequest.set(null);
            deltaBuffer.forceFlush();
            safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
            safePut(new RenderEvent.FinalizeMessage());
        }
    }

    private void handleCommand(InputEvent.ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
                RequestContext ctx = currentRequest.getAndSet(null);
                if (ctx != null) {
                    chatService.cancel(ctx);
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
            case CLEAR -> {
                deltaBuffer.forceFlush();
                sessionManager.clear();
                safePut(new RenderEvent.ClearChat());
            }
            case THEME -> {
                deltaBuffer.forceFlush();
                Theme newTheme = resolveTheme(cmd.args().trim().toLowerCase());
                if (newTheme != null) {
                    this.theme = newTheme;
                    safePut(new RenderEvent.ThemeChange(newTheme));
                }
            }
            case EXIT, QUIT -> handleShutdown();
            case HELP -> {
                deltaBuffer.forceFlush();
                safePut(new RenderEvent.AddSystemMessage("""
                    Commands:
                      /exit       - Exit LavenderCode
                      /clear      - Clear conversation history
                      /help       - Show this help
                      /theme dark - Switch to dark theme
                      /theme light- Switch to light theme
                    Keyboard:
                      Ctrl+C      - Cancel current request
                      Ctrl+D (empty) - Exit
                      Alt+Enter   - Insert newline"""));
            }
            case SCROLL -> {
                deltaBuffer.forceFlush();
                RenderEvent se = parseScrollEvent(cmd.args());
                if (se != null) safePut(se);
            }
        }
    }

    private void handleShutdown() {
        RequestContext ctx = currentRequest.getAndSet(null);
        if (ctx != null) chatService.cancel(ctx);
        deltaBuffer.forceFlush();
        safePut(new RenderEvent.Shutdown());
    }

    private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
        if (currentRequest.get() != ctx) return;

        switch (delta) {
            case DeltaEvent.Content(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.CONTENT_DELTA, t, 0));
            case DeltaEvent.Thinking(String t) ->
                deltaBuffer.append(new DeltaBuffer.BufferedEvent(DeltaBuffer.BufferedEvent.Type.THINK_DELTA, t, 0));
            case DeltaEvent.Usage(int i, int o) ->
                safePut(new RenderEvent.StatusUpdate(modelName, i + o, false));
            case DeltaEvent.Complete() -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
            case DeltaEvent.Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    deltaBuffer.forceFlush();
                    safePut(new RenderEvent.AddSystemMessage("[Error] " + m));
                    safePut(new RenderEvent.FinalizeMessage());
                }
            }
        }
    }

    private Theme resolveTheme(String name) {
        return switch (name) {
            case "dark" -> Theme.dark();
            case "light" -> Theme.light();
            default -> null;
        };
    }

    private RenderEvent parseScrollEvent(String args) {
        return switch (args.trim().toLowerCase()) {
            case "up"        -> new RenderEvent.ScrollDelta(-1);
            case "down"      -> new RenderEvent.ScrollDelta(1);
            case "page-up"   -> new RenderEvent.ScrollDelta(-20);
            case "page-down" -> new RenderEvent.ScrollDelta(20);
            case "top"       -> new RenderEvent.ScrollTo(0);
            case "bottom"    -> new RenderEvent.ScrollAutoReset();
            default          -> null;
        };
    }

    private void safePut(RenderEvent event) {
        try {
            renderQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run test — verify GREEN**

Run: `mvn test -Dtest=NetworkOrchestratorTest -q`
Expected: Tests run: 5, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java \
        src/test/java/com/lavendercode/chat/terminal/NetworkOrchestratorTest.java
git commit -m "$(cat <<'EOF'
feat: add NetworkOrchestrator — event dispatch with cancel/commands (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: Terminal Rendering (Task 10)

### Task 10: Create TerminalRenderer

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/TerminalRenderer.java`

> Note: TerminalRenderer has a hard dependency on JLine3's Terminal interface, which can't be fully unit-tested without a real terminal. The rendering correctness is verified in the integration test (Task 13). This task focuses on writing the implementation with the satisfaction that all types compile correctly.

- [ ] **Step 1: Write TerminalRenderer.java**

Write `TerminalRenderer.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;
import org.jline.utils.Signals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class TerminalRenderer {

    private final Terminal terminal;
    private final BlockingQueue<RenderEvent> renderQueue;
    private final List<MessageBlock> blocks;
    private MessageBlock currentAIBlock;
    private int viewportStart;
    private boolean autoScroll = true;
    private Theme theme;
    private String modelName = "";
    private int tokenCount = 0;

    private static final int STATUS_HEIGHT = 1;
    private int viewportHeight;

    public TerminalRenderer(Terminal terminal, BlockingQueue<RenderEvent> renderQueue,
                            Theme theme) {
        this.terminal = terminal;
        this.renderQueue = renderQueue;
        this.blocks = new ArrayList<>();
        this.theme = theme;
        this.viewportHeight = Math.max(1, terminal.getHeight() - STATUS_HEIGHT - 1);
    }

    public void run() {
        registerResizeHandler();
        terminal.puts(InfoCmp.Capability.enter_ca_mode);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.flush();

        try {
            while (true) {
                RenderEvent event = renderQueue.take();
                if (event instanceof RenderEvent.Shutdown) break;
                dispatch(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminal.puts(InfoCmp.Capability.cursor_visible);
            terminal.puts(InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
        }
    }

    private void dispatch(RenderEvent event) {
        switch (event) {
            case RenderEvent.AppendToMessage(var text) -> appendToAIBlock(text);
            case RenderEvent.FinalizeMessage() -> {
                if (currentAIBlock != null) {
                    currentAIBlock.markComplete();
                    currentAIBlock = null;
                }
            }
            case RenderEvent.AddUserMessage(var text) -> addBlock(Role.USER, text);
            case RenderEvent.AddSystemMessage(var text) -> addBlock(Role.SYSTEM, text);
            case RenderEvent.ThinkDelta(var text) -> appendThinking(text);
            case RenderEvent.ClearChat() -> {
                blocks.clear();
                currentAIBlock = null;
                viewportStart = 0;
                tokenCount = 0;
                drawFull();
            }
            case RenderEvent.ScrollTo(int n) -> {
                viewportStart = clampValue(n);
                autoScroll = false;
                drawViewport();
            }
            case RenderEvent.ScrollDelta(int d) -> scrollDelta(d);
            case RenderEvent.ScrollAutoReset() -> {
                autoScroll = true;
                scrollToBottom();
                drawViewport();
            }
            case RenderEvent.WindowResize(int c, int r) -> {
                viewportHeight = r - STATUS_HEIGHT - 1;
                reflowAll();
                drawFull();
            }
            case RenderEvent.ThemeChange(var t) -> {
                this.theme = t;
                drawFull();
            }
            case RenderEvent.StatusUpdate(var m, int tc, boolean __) -> {
                this.modelName = m;
                this.tokenCount = tc;
                drawStatusBar();
            }
            case RenderEvent.RefreshAll() -> drawFull();
        }
    }

    // ===== drawing =====

    private void drawFull() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        drawStatusBar();
        drawViewport();
    }

    private void drawStatusBar() {
        String status = String.format("Model: %s | Tokens: %d | Theme: %s",
            modelName, tokenCount, theme.name());
        AttributedString styled = theme.apply(StyleCatalog.STATUS_BAR,
            padRight(status, terminal.getWidth()));
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.writer().print(styled.toAnsi(terminal));
        terminal.flush();
    }

    private void drawViewport() {
        clampViewport();
        int totalLines = totalContentLines();

        for (int screenRow = STATUS_HEIGHT; screenRow < terminal.getHeight() - 1; screenRow++) {
            int contentIdx = viewportStart + (screenRow - STATUS_HEIGHT);
            terminal.puts(InfoCmp.Capability.cursor_address, screenRow, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);

            if (contentIdx < totalLines) {
                RenderedLine line = getRenderedLine(contentIdx);
                if (line != null) {
                    for (AttributedString seg : line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(screenRow, totalLines);
        }
        terminal.flush();
    }

    private void drawDiff(int startRow, int count) {
        int endRow = Math.min(startRow + count, terminal.getHeight() - 1);
        int totalLines = totalContentLines();
        for (int row = startRow; row < endRow; row++) {
            terminal.puts(InfoCmp.Capability.cursor_address, row, 0);
            terminal.puts(InfoCmp.Capability.clr_eol);

            int contentIdx = viewportStart + (row - STATUS_HEIGHT);
            if (contentIdx >= 0 && contentIdx < totalLines) {
                RenderedLine line = getRenderedLine(contentIdx);
                if (line != null) {
                    for (AttributedString seg : line.segments()) {
                        terminal.writer().print(seg.toAnsi(terminal));
                    }
                }
            }
            drawScrollbarCell(row, totalLines);
        }
        terminal.flush();
    }

    private void drawScrollbarCell(int screenRow, int totalLines) {
        if (totalLines <= viewportHeight) return;
        int sbCol = terminal.getWidth() - 1;
        terminal.puts(InfoCmp.Capability.cursor_address, screenRow, sbCol);

        double ratio = (double) viewportStart / Math.max(1, totalLines - viewportHeight);
        int thumbRow = STATUS_HEIGHT + (int) (ratio * (viewportHeight - 1));

        if (screenRow == thumbRow) {
            terminal.writer().print(theme.apply(StyleCatalog.SCROLLBAR_THUMB, "\u2588").toAnsi(terminal));
        } else {
            terminal.writer().print(theme.apply(StyleCatalog.SCROLLBAR_TRACK, "\u2502").toAnsi(terminal));
        }
    }

    // ===== block management =====

    private void appendToAIBlock(String text) {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
        }

        int oldCount = currentAIBlock.lineCount();
        int width = Math.max(1, terminal.getWidth() - 2);
        currentAIBlock.append(text, width);
        int added = currentAIBlock.lineCount() - oldCount;

        if (added > 0) {
            int firstRow = STATUS_HEIGHT + (blockToGlobalRow(currentAIBlock) + oldCount - viewportStart);
            drawDiff(firstRow, Math.min(added + 1, viewportHeight));
        }

        if (autoScroll) {
            scrollToBottom();
            drawViewport();
        }
    }

    private void appendThinking(String text) {
        if (currentAIBlock == null) {
            currentAIBlock = new MessageBlock(Role.ASSISTANT);
            blocks.add(currentAIBlock);
        }

        int width = Math.max(1, terminal.getWidth() - 4);
        currentAIBlock.appendThinking(text, width);

        if (autoScroll) {
            scrollToBottom();
            drawViewport();
        }
    }

    private void addBlock(Role role, String text) {
        MessageBlock block = new MessageBlock(role);
        int width = Math.max(1, terminal.getWidth() - 2);
        block.append(text, width);
        block.markComplete();
        blocks.add(block);

        if (autoScroll) {
            scrollToBottom();
        }
        drawViewport();
    }

    // ===== scrolling =====

    private void scrollDelta(int delta) {
        int oldStart = viewportStart;
        viewportStart += delta;
        clampViewport();
        if (viewportStart != oldStart) {
            drawViewport();
            if (viewportStart == maxViewportStart()) {
                autoScroll = true;
            }
        }
    }

    private void scrollToBottom() {
        viewportStart = maxViewportStart();
    }

    private int clampValue(int n) {
        return Math.max(0, Math.min(n, maxViewportStart()));
    }

    private void clampViewport() {
        viewportStart = Math.max(0, Math.min(viewportStart, maxViewportStart()));
    }

    private int maxViewportStart() {
        return Math.max(0, totalContentLines() - viewportHeight);
    }

    // ===== helpers =====

    private int totalContentLines() {
        return blocks.stream().mapToInt(MessageBlock::lineCount).sum();
    }

    private int blockToGlobalRow(MessageBlock block) {
        int row = 0;
        for (MessageBlock b : blocks) {
            if (b == block) return row;
            row += b.lineCount();
        }
        return row;
    }

    private RenderedLine getRenderedLine(int globalIndex) {
        int remaining = globalIndex;
        for (MessageBlock block : blocks) {
            List<RenderedLine> lines = block.allLines();
            if (remaining < lines.size()) {
                return lines.get(remaining);
            }
            remaining -= lines.size();
        }
        return null;
    }

    private void reflowAll() {
        int width = Math.max(1, terminal.getWidth() - 2);
        for (MessageBlock block : blocks) {
            block.reflow(width);
        }
        clampViewport();
    }

    private String padRight(String s, int width) {
        if (width <= s.length()) return s;
        return s + " ".repeat(width - s.length());
    }

    private void registerResizeHandler() {
        Signals.register("WINCH", sig -> {
            safePut(new RenderEvent.WindowResize(
                terminal.getWidth(), terminal.getHeight()));
        });
    }

    private void safePut(RenderEvent event) {
        try {
            renderQueue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 2: Verify compilation with all existing classes**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/TerminalRenderer.java
git commit -m "$(cat <<'EOF'
feat: add TerminalRenderer with drawFull/drawDiff and resize handling

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5: Input & Entry Point (Tasks 11-12)

### Task 11: Create InputSystem

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/InputSystem.java`

- [ ] **Step 1: Write InputSystem.java**

```java
package com.lavendercode.chat.terminal;

import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Input system using JLine3 LineReader.
 * Enter = submit, Alt+Enter = newline, Ctrl+C = cancel, Ctrl+D = shutdown.
 */
public class InputSystem {

    private final LineReader reader;
    private final BlockingQueue<InputEvent> inputQueue;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public InputSystem(Terminal terminal, BlockingQueue<InputEvent> inputQueue) {
        this.inputQueue = inputQueue;

        Path historyPath = Path.of(System.getProperty("user.home"), ".lavendercode_history");

        LineReaderBuilder builder = LineReaderBuilder.builder()
            .terminal(terminal)
            .variable(LineReader.HISTORY_FILE, historyPath)
            .variable(LineReader.HISTORY_SIZE, 1000)
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, "  ")
            .variable(LineReader.INDENTATION, 2)
            .completer(new StringsCompleter(
                "/exit", "/quit", "/clear", "/help",
                "/theme dark", "/theme light", "/scroll top",
                "/scroll bottom", "/scroll up", "/scroll down"));

        this.reader = builder.build();

        // Alt+Enter -> insert newline
        KeyMap<Binding> keyMap = reader.getKeyMaps().get(LineReader.MAIN);
        keyMap.bind(new Macro(LineReader.SELF_INSERT), KeyMap.alt(KeyMap.ctrl('J')));
    }

    /**
     * Blocking loop that reads user input and publishes InputEvents.
     * Runs on InputThread until shutdown.
     */
    public void run() {
        while (!shutdown.get()) {
            String line;
            try {
                // Show prompt and read input
                terminal().puts(InfoCmp.Capability.cursor_address,
                    terminal().getHeight() - 2, 0);
                terminal().flush();
                line = reader.readLine("> ");
            } catch (UserInterruptException e) {
                // Ctrl+C -> cancel
                inputQueue.offer(new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, ""));
                continue;
            } catch (EndOfFileException e) {
                inputQueue.offer(new InputEvent.Shutdown());
                break;
            }

            if (line == null) {
                inputQueue.offer(new InputEvent.Shutdown());
                break;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("/")) {
                inputQueue.offer(parseCommand(trimmed));
            } else {
                inputQueue.offer(new InputEvent.SendMessage(line));
            }
        }
    }

    public void requestShutdown() {
        shutdown.set(true);
        reader.getTerminal().reader().interrupt();
    }

    private org.jline.terminal.Terminal terminal() {
        return reader.getTerminal();
    }

    private InputEvent parseCommand(String input) {
        String line = input.toLowerCase();
        if (line.equals("/exit") || line.equals("/quit"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, "");
        if (line.equals("/clear"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, "");
        if (line.equals("/help"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
        if (line.startsWith("/theme "))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.THEME,
                input.substring(7).trim());
        if (line.startsWith("/scroll"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.SCROLL,
                input.substring(8).trim());
        if (line.equals("/cancel"))
            return new InputEvent.ExecuteCommand(InputEvent.CommandType.CANCEL, "");

        return new InputEvent.ExecuteCommand(InputEvent.CommandType.HELP, "");
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/InputSystem.java
git commit -m "$(cat <<'EOF'
feat: add InputSystem with JLine3 LineReader and key bindings

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: Create TerminalChatApplication + Modify LavenderCode.java

**Files:**
- Create: `src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java`
- Modify: `src/main/java/com/lavendercode/LavenderCode.java`

- [ ] **Step 1: Write TerminalChatApplication.java**

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import org.jline.terminal.Terminal;

import java.util.concurrent.*;

/**
 * Orchestrates the four-thread terminal chat application lifecycle.
 */
public class TerminalChatApplication {

    private final SessionManager sessionManager;
    private final LlmProvider provider;
    private final String modelName;
    private final LlmConfig config;
    private final Theme theme;

    public TerminalChatApplication(SessionManager sessionManager,
                                   LlmProvider provider,
                                   String modelName,
                                   LlmConfig config,
                                   Theme theme) {
        this.sessionManager = sessionManager;
        this.provider = provider;
        this.modelName = modelName;
        this.config = config;
        this.theme = theme;
    }

    public void run(Terminal terminal) throws Exception {
        BlockingQueue<InputEvent> inputQueue = new LinkedBlockingQueue<>();
        BlockingQueue<RenderEvent> renderQueue = new LinkedBlockingQueue<>();

        ScheduledExecutorService timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lavender-timer");
            t.setDaemon(true);
            return t;
        });

        // Components
        DeltaBuffer deltaBuffer = new DeltaBuffer(timerScheduler, renderQueue);
        ChatService chatService = new StreamingChatService();
        NetworkOrchestrator orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, modelName, config, theme
        );
        TerminalRenderer renderer = new TerminalRenderer(terminal, renderQueue, theme);
        InputSystem inputSystem = new InputSystem(terminal, inputQueue);

        // Threads
        Thread inputThread = new Thread(inputSystem::run, "lavender-input");
        Thread networkThread = new Thread(orchestrator::run, "lavender-network");
        Thread renderThread = new Thread(renderer::run, "lavender-render");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            inputSystem.requestShutdown();
            try { renderQueue.put(new RenderEvent.Shutdown()); } catch (InterruptedException ignored) {}
        }));

        inputThread.start();
        networkThread.start();
        renderThread.start();

        // Wait for render thread to finish (it exits on Shutdown event)
        renderThread.join();

        // Cleanup
        timerScheduler.shutdownNow();
        inputThread.interrupt();
        networkThread.interrupt();
    }
}
```

- [ ] **Step 2: Modify LavenderCode.java — add --ui=v2 branch**

Read the current file, then replace the main method body to support both v1 and v2:

```java
package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.TerminalChatApplication;
import com.lavendercode.chat.terminal.Theme;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.provider.LlmProvider;
import com.lavendercode.core.provider.ProviderRegistry;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;

public class LavenderCode {

    public static void main(String[] args) throws Exception {
        // Parse flags
        Path configPath = Path.of("config.yaml");
        String uiVersion = "v2"; // default to new terminal UI

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            } else if (args[i].startsWith("--ui=")) {
                uiVersion = args[i].substring(5);
            }
        }

        // Load config
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

        System.out.println("Loaded config for protocol: " + config.provider().protocol());
        System.out.println("Model: " + config.provider().model());

        LlmProvider provider = ProviderRegistry.get(config.provider().protocol());
        SessionManager sessionManager = new InMemorySessionManager();

        if ("v2".equals(uiVersion)) {
            // New JLine3 native terminal path
            Terminal terminal = TerminalBuilder.builder()
                .name("LavenderCode")
                .system(true)
                .build();

            TerminalChatApplication app = new TerminalChatApplication(
                sessionManager, provider,
                config.provider().model(), config,
                Theme.dark()
            );
            app.run(terminal);
        } else {
            // Old Lanterna path (fallback)
            runLegacyUi(sessionManager, provider, config);
        }
    }

    private static void runLegacyUi(SessionManager sessionManager,
                                    LlmProvider provider, LlmConfig config) throws Exception {
        // Keep existing Lanterna code here (createScreen + TuiApplication)
        // This block is unchanged from the current LavenderCode.java
        // ... existing createScreen() and TuiApplication code ...
        System.err.println("Legacy UI (--ui=v1) is no longer available in this version.");
        System.err.println("Please use the new terminal UI: remove --ui=v1 flag.");
        System.exit(1);
    }
}
```

> Note: Keep the existing `createScreen()` and `createSwingScreen()` methods in `LavenderCode.java` for now. They will be removed in Phase 6 (Task 15).

- [ ] **Step 3: Run all unit tests to verify no regressions**

Run: `mvn test -q`
Expected: All tests pass (existing + new)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java \
        src/main/java/com/lavendercode/LavenderCode.java
git commit -m "$(cat <<'EOF'
feat: add TerminalChatApplication and --ui=v2 entry point

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6: Integration & Cleanup (Tasks 13-15)

### Task 13: Create Integration Test with DumbTerminal

**Files:**
- Create: `src/test/java/com/lavendercode/chat/terminal/TerminalChatIntegrationTest.java`

- [ ] **Step 1: Write the integration test (RED — no implementation to test, just write test first)**

Write `TerminalChatIntegrationTest.java`:

```java
package com.lavendercode.chat.terminal;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.config.LlmConfig;
import com.lavendercode.core.config.ProviderConfig;
import com.lavendercode.core.provider.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test: InputEvent -> NetworkOrchestrator -> RenderEvent pipeline.
 * Uses JLine3 DumbTerminal (headless, no real TTY needed).
 */
class TerminalChatIntegrationTest {

    private Terminal terminal;
    private BlockingQueue<InputEvent> inputQueue;
    private BlockingQueue<RenderEvent> renderQueue;
    private ScheduledExecutorService scheduler;
    private DeltaBuffer deltaBuffer;
    private NetworkOrchestrator orchestrator;
    private LlmProvider provider;
    private ChatService chatService;
    private SessionManager sessionManager;
    private Thread networkThread;

    @BeforeEach
    void setUp() throws Exception {
        terminal = TerminalBuilder.builder()
            .dumb(true)
            .build();

        inputQueue = new LinkedBlockingQueue<>();
        renderQueue = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        deltaBuffer = new DeltaBuffer(scheduler, renderQueue);
        sessionManager = new InMemorySessionManager();

        provider = mock(LlmProvider.class);
        when(provider.protocol()).thenReturn("openai-compatible");

        chatService = new StreamingChatService();

        LlmConfig config = new LlmConfig(
            new ProviderConfig("gpt-4", "openai-compatible", "http://localhost", "key", null), null);

        orchestrator = new NetworkOrchestrator(
            chatService, deltaBuffer, renderQueue, inputQueue,
            sessionManager, provider, "gpt-4", config, Theme.dark()
        );

        networkThread = new Thread(orchestrator::run, "lavender-network-test");
        networkThread.start();
    }

    @AfterEach
    void tearDown() {
        networkThread.interrupt();
        scheduler.shutdownNow();
    }

    @Test
    void shouldRenderUserMessage() throws Exception {
        inputQueue.put(new InputEvent.SendMessage("hello"));
        inputQueue.put(new InputEvent.Shutdown());

        RenderEvent e1 = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(e1).isInstanceOf(RenderEvent.AddUserMessage.class);
        assertThat(((RenderEvent.AddUserMessage) e1).text()).isEqualTo("hello");
    }

    @Test
    void clearCommandShouldEmitClearChat() throws Exception {
        inputQueue.put(new InputEvent.SendMessage("msg1"));
        // drain the AddUserMessage
        renderQueue.poll(2, TimeUnit.SECONDS);

        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.CLEAR, ""));
        inputQueue.put(new InputEvent.Shutdown());

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.ClearChat.class);
    }

    @Test
    void exitCommandShouldEmitShutdown() throws Exception {
        inputQueue.put(new InputEvent.ExecuteCommand(InputEvent.CommandType.EXIT, ""));

        RenderEvent event = renderQueue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isInstanceOf(RenderEvent.Shutdown.class);
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `mvn test -Dtest=TerminalChatIntegrationTest -q`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 3: Run ALL tests to verify complete integration**

Run: `mvn test -q`
Expected: All tests pass — existing core tests + all new terminal tests

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/lavendercode/chat/terminal/TerminalChatIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat: add integration test with DumbTerminal for event pipeline (TDD)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 14: Verify all unit tests pass (pre-cleanup checkpoint)

- [ ] **Step 1: Run full test suite**

Run: `mvn clean test`
Expected: BUILD SUCCESS, all tests pass (target: ~30+ tests across all test classes)

Count should include tests from:
- Existing: core provider tests, config tests, SSE parser tests
- New Phase 1: InputEventTest (4), RenderEventTest (17), DataTypesTest (12)
- New Phase 2: MessageBlockTest (11), DeltaBufferTest (7)
- New Phase 3: RequestContextTest (4), StreamingChatServiceTest (3), NetworkOrchestratorTest (5)
- New Phase 6: TerminalChatIntegrationTest (3)

- [ ] **Step 2: Commit if any cleanup was needed**

```bash
git commit -m "$(cat <<'EOF'
chore: full test suite verification before cleanup phase

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: Remove Lanterna, old TuiApplication, and simplify LavenderCode.java

**Files:**
- Modify: `pom.xml` — remove Lanterna dependency
- Delete: `src/main/java/com/lavendercode/chat/tui/TuiApplication.java`
- Delete: `src/test/java/com/lavendercode/chat/tui/TuiApplicationTest.java`
- Modify: `src/main/java/com/lavendercode/LavenderCode.java` — remove Lanterna/Swing imports and methods

- [ ] **Step 1: Remove Lanterna dependency from pom.xml**

Delete lines 58-63 (the entire Lanterna dependency block):
```xml
        <!-- Lanterna TUI -->
        <dependency>
            <groupId>com.googlecode.lanterna</groupId>
            <artifactId>lanterna</artifactId>
            <version>3.1.2</version>
        </dependency>
```

- [ ] **Step 2: Delete TuiApplication.java**

```bash
rm src/main/java/com/lavendercode/chat/tui/TuiApplication.java
```

- [ ] **Step 3: Delete TuiApplicationTest.java**

```bash
rm src/test/java/com/lavendercode/chat/tui/TuiApplicationTest.java
```

- [ ] **Step 4: Simplify LavenderCode.java**

Remove all Lanterna/Swing imports and legacy code. The final `LavenderCode.java` should be:

```java
package com.lavendercode;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.chat.terminal.TerminalChatApplication;
import com.lavendercode.chat.terminal.Theme;
import com.lavendercode.core.config.ConfigLoader;
import com.lavendercode.core.config.LlmConfig;
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

        System.out.println("Loaded config for protocol: " + config.provider().protocol());
        System.out.println("Model: " + config.provider().model());

        LlmProvider provider = ProviderRegistry.get(config.provider().protocol());
        SessionManager sessionManager = new InMemorySessionManager();

        Terminal terminal = TerminalBuilder.builder()
            .name("LavenderCode")
            .system(true)
            .build();

        TerminalChatApplication app = new TerminalChatApplication(
            sessionManager, provider,
            config.provider().model(), config,
            Theme.dark()
        );
        app.run(terminal);
    }
}
```

- [ ] **Step 5: Run all tests to verify cleanup**

Run: `mvn clean test`
Expected: BUILD SUCCESS, all tests pass (no Lanterna tests, no TuiApplication tests)

- [ ] **Step 6: Commit**

```bash
git add pom.xml LavenderCode.java
git rm src/main/java/com/lavendercode/chat/tui/TuiApplication.java \
       src/test/java/com/lavendercode/chat/tui/TuiApplicationTest.java
git commit -m "$(cat <<'EOF'
refactor: remove Lanterna dependency and old TuiApplication

Replace Lanterna gui2 TUI with JLine3 native terminal rendering.
Delete TuiApplication.java and its tests.
Simplify LavenderCode.java to use TerminalChatApplication directly.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Full Test Suite Verification

After all tasks are complete, run the full verification:

```bash
cd "E:\learn\code\agent_learn\Lavendercode"
mvn clean test
```

Expected: BUILD SUCCESS with approximately 60+ tests passing across all modules.

---

## Design-Spec Compliance Checklist

```
AC-01: Real terminal (not Swing)            → Task 12 (TerminalChatApplication uses JLine3 TerminalBuilder)
AC-02: Streaming typewriter output          → Task 6 (DeltaBuffer 50ms timer), Task 10 (appendToAIBlock drawDiff)
AC-03: Code block highlight                 → Task 5 (MessageBlock fence detection + ANSI background)
AC-04: Token count in status bar            → Task 10 (StatusUpdate + drawStatusBar)
AC-05: Scroll UP/DOWN/PgUp/PgDn             → Task 9 (parseScrollEvent), Task 10 (scrollDelta/drawViewport)
AC-06: Window resize + reflow               → Task 10 (registerResizeHandler + reflowAll)
AC-07: Theme switch (/theme light|dark)     → Task 4 (Theme.dark()/light()), Task 9 (THEME command), Task 10 (ThemeChange)
AC-08: Long message auto-wrap               → Task 5 (wrapAndColor with width parameter)
AC-09: Enter submit / Alt+Enter newline     → Task 11 (InputSystem key bindings)
AC-10: Cancel UI cleanup                    → Task 9 (CANCEL handler: forceFlush + AddSystemMessage + FinalizeMessage)
AC-11: 100+ round performance               → Task 10 (drawDiff partial rendering)
AC-12: Ctrl+C interrupts request            → Task 11 (UserInterruptException catch → CANCEL command)
```

---

## File Manifest

### New files (14 main + 8 test = 22 total):

**Main source:**
- `src/main/java/com/lavendercode/chat/terminal/InputEvent.java`
- `src/main/java/com/lavendercode/chat/terminal/RenderEvent.java`
- `src/main/java/com/lavendercode/chat/terminal/DeltaEvent.java`
- `src/main/java/com/lavendercode/chat/terminal/StyleCatalog.java`
- `src/main/java/com/lavendercode/chat/terminal/RenderedLine.java`
- `src/main/java/com/lavendercode/chat/terminal/Theme.java`
- `src/main/java/com/lavendercode/chat/terminal/MessageBlock.java`
- `src/main/java/com/lavendercode/chat/terminal/DeltaBuffer.java`
- `src/main/java/com/lavendercode/chat/terminal/ChatService.java`
- `src/main/java/com/lavendercode/chat/terminal/RequestContext.java`
- `src/main/java/com/lavendercode/chat/terminal/StreamingChatService.java`
- `src/main/java/com/lavendercode/chat/terminal/NetworkOrchestrator.java`
- `src/main/java/com/lavendercode/chat/terminal/TerminalRenderer.java`
- `src/main/java/com/lavendercode/chat/terminal/InputSystem.java`
- `src/main/java/com/lavendercode/chat/terminal/TerminalChatApplication.java`

**Tests:**
- `src/test/java/com/lavendercode/chat/terminal/InputEventTest.java`
- `src/test/java/com/lavendercode/chat/terminal/RenderEventTest.java`
- `src/test/java/com/lavendercode/chat/terminal/DataTypesTest.java`
- `src/test/java/com/lavendercode/chat/terminal/MessageBlockTest.java`
- `src/test/java/com/lavendercode/chat/terminal/DeltaBufferTest.java`
- `src/test/java/com/lavendercode/chat/terminal/RequestContextTest.java`
- `src/test/java/com/lavendercode/chat/terminal/StreamingChatServiceTest.java`
- `src/test/java/com/lavendercode/chat/terminal/NetworkOrchestratorTest.java`
- `src/test/java/com/lavendercode/chat/terminal/TerminalChatIntegrationTest.java`

### Modified files:
- `pom.xml` — add JLine3, remove Lanterna
- `src/main/java/com/lavendercode/LavenderCode.java` — JLine3 terminal path

### Deleted files:
- `src/main/java/com/lavendercode/chat/tui/TuiApplication.java`
- `src/test/java/com/lavendercode/chat/tui/TuiApplicationTest.java`
