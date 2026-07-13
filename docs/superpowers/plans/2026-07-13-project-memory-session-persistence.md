# 项目记忆与会话持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现跨会话的项目指令装载、JSONL 会话持久化与 `/resume` 恢复、以及条件触发的自动笔记，使 Agent 启动即可遵循规范并可持续续聊。

**Architecture:** 方案 1——用 `PersistingSessionManager` 装饰现有 `InMemorySessionManager` 写 JSONL；`InstructionLoader` / `MemoryService` 独立服务；`SessionHandle` 暴露并可在 `/resume` 时切换 sessionId 与路径；`SystemPromptAssembler` 扩展为配置(8)/文件指令(9)/记忆(10) 三槽位。类型名对齐 LavenderCode（不用 Spec 中的 Conversation/Done 等）。

**Tech Stack:** Java 21、JUnit 5、AssertJ、Mockito、Jackson、JLine3、Maven（`mvn -q -Dtest=ClassName test`）。

**Spec:** `docs/superpowers/specs/2026-07-13-project-memory-session-persistence-design.md`  
**PRD:** `docs/current/modules/memory/PRD_项目记忆与会话持久化.md`

---

## File Structure

### Create

| Path | Responsibility |
|---|---|
| `src/main/java/com/lavendercode/core/context/SessionHandle.java` | 持有可变 sessionId/`SessionPaths`/`ContextManager`；`rebind` |
| `src/main/java/com/lavendercode/chat/session/SessionTranscriptWriter.java` | JSONL 追加、compact 行、锁+刷盘、Closeable |
| `src/main/java/com/lavendercode/chat/session/PersistingSessionManager.java` | 装饰 SessionManager；suspend 持久化（供 resume） |
| `src/main/java/com/lavendercode/chat/session/SessionCatalog.java` | 扫描有效新格式会话、列表元数据 |
| `src/main/java/com/lavendercode/chat/session/SessionListItem.java` | 列表项 record |
| `src/main/java/com/lavendercode/chat/session/SessionRestorer.java` | 恢复流水线 |
| `src/main/java/com/lavendercode/chat/session/SessionCleanup.java` | 启动后台清理 30 天+ |
| `src/main/java/com/lavendercode/chat/session/RelativeTime.java` | 相对时间文案 |
| `src/main/java/com/lavendercode/chat/terminal/SessionPicker.java` | JLine 选择 UI；过滤逻辑可单测 |
| `src/main/java/com/lavendercode/chat/terminal/SessionPickerModel.java` | 纯逻辑：过滤、选中索引 |
| `src/main/java/com/lavendercode/core/prompt/InstructionLoader.java` | 三层加载 + @include |
| `src/main/java/com/lavendercode/core/memory/MemoryNoteType.java` | 四类枚举 |
| `src/main/java/com/lavendercode/core/memory/MemoryService.java` | 索引/异步更新/锁 |
| `src/main/java/com/lavendercode/core/memory/MemoryAction.java` | LLM 返回操作模型 |
| 对应 `src/test/java/...` | 各组件单测 |

### Modify

| Path | Change |
|---|---|
| `SessionIdGenerator.java` | 新 ID 格式 |
| `SessionIdGeneratorTest.java` | 新正则 |
| `SessionPaths.java` | 增加 `conversationJsonl()`；可保留构造 |
| `Layer1Offloader.java` | 经 `SessionHandle.paths()` 取路径（resume 可切换） |
| `ContextBootstrap.java` | 返回 `SessionHandle` |
| `SystemPromptAssembler.java` | `assemble(config, instructions, memory)`；旧单参委托 |
| `SystemPromptAssemblerTest.java` | 三参与兼容 |
| `LavenderCode.java` | 启动装载指令/记忆/清理/包装 SessionManager |
| `LavenderCodeSessionInitTest.java` | 适配 `SessionHandle` |
| `TerminalChatApplication.java` | 注入 instructions/memory/handle/writer 相关依赖 |
| `NetworkOrchestrator.java` | assemble 三参、turnCount、Complete→memory、`/resume`、互斥 |
| `BuiltinCommandRegistry.java` / `InputEvent.java` | `RESUME` |
| 凡直接调 `ContextBootstrap.create` 且断言 `ContextManager` 的测试 | 改为 `handle.contextManager()` |

---

## Phase 1 — Session 身份与 JSONL（AC7–AC10, AC28）

### Task 1: SessionIdGenerator 新格式

**Files:**
- Modify: `src/main/java/com/lavendercode/core/context/SessionIdGenerator.java`
- Modify: `src/test/java/com/lavendercode/core/context/SessionIdGeneratorTest.java`

- [ ] **Step 1: 改写失败测试**

```java
@Test
void formatIsDateTimeDashHex4() {
    String id = SessionIdGenerator.generate();
    assertThat(id).matches("\\d{8}-\\d{6}-[0-9a-f]{4}");
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=SessionIdGeneratorTest#formatIsDateTimeDashHex4 test`  
Expected: FAIL（仍为 epoch-6 格式）或旧测试名不匹配——先改测试名替换旧测试。

- [ ] **Step 3: 实现**

```java
package com.lavendercode.core.context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SessionIdGenerator {
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionIdGenerator() {}

    public static String generate() {
        String stamp = LocalDateTime.now().format(FMT);
        return stamp + "-" + randomHex4();
    }

    static String randomHex4() {
        int v = RANDOM.nextInt(0x10000);
        return String.format("%04x", v);
    }

    /** 新格式可解析；旧格式返回 empty */
    public static boolean isNewFormat(String sessionId) {
        return sessionId != null && sessionId.matches("\\d{8}-\\d{6}-[0-9a-f]{4}");
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=SessionIdGeneratorTest test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/core/context/SessionIdGenerator.java src/test/java/com/lavendercode/core/context/SessionIdGeneratorTest.java
git commit -m "功能(会话): 会话 ID 改为 YYYYMMDD-HHMMSS-xxxx 格式"
```

---

### Task 2: SessionHandle + Bootstrap 返回值 + Layer1 可切换路径

**Files:**
- Create: `src/main/java/com/lavendercode/core/context/SessionHandle.java`
- Modify: `src/main/java/com/lavendercode/core/context/SessionPaths.java`
- Modify: `src/main/java/com/lavendercode/core/context/Layer1Offloader.java`
- Modify: `src/main/java/com/lavendercode/core/context/ContextBootstrap.java`
- Modify: `src/main/java/com/lavendercode/core/context/CompactionService.java`（若持有 SessionPaths 引用则改走 Handle；否则仅 Layer1）
- Modify: `src/test/java/com/lavendercode/LavenderCodeSessionInitTest.java`
- Create: `src/test/java/com/lavendercode/core/context/SessionHandleTest.java`

- [ ] **Step 1: 写 SessionHandle 测试**

```java
@Test
void rebindUpdatesPaths(@TempDir Path root) throws Exception {
    SessionPaths p1 = new SessionPaths(root, "20260101-120000-aaaa");
    p1.ensureDirectories();
    SessionHandle h = new SessionHandle("20260101-120000-aaaa", p1, NoOpContextManager.INSTANCE);
    h.rebind(root, "20260102-130000-bbbb");
    assertThat(h.sessionId()).isEqualTo("20260102-130000-bbbb");
    assertThat(h.paths().sessionRoot())
        .isEqualTo(root.resolve(".lavendercode/sessions/20260102-130000-bbbb"));
}
```

- [ ] **Step 2: 实现 SessionHandle + SessionPaths 暴露**

`SessionPaths` 增加：

```java
public Path sessionRoot() { return sessionRoot; }
public Path conversationJsonl() { return sessionRoot.resolve("conversation.jsonl"); }
```

（若 `sessionRoot` 字段已是 private final，确保有 getter；当前类已有 `toolResultsDir`。）

`SessionHandle`:

```java
public final class SessionHandle {
    private volatile String sessionId;
    private volatile SessionPaths paths;
    private final ContextManager contextManager;
    private final Path projectRoot;

    public SessionHandle(Path projectRoot, String sessionId, SessionPaths paths, ContextManager cm) {
        this.projectRoot = projectRoot;
        this.sessionId = sessionId;
        this.paths = paths;
        this.contextManager = cm;
    }

    public String sessionId() { return sessionId; }
    public SessionPaths paths() { return paths; }
    public ContextManager contextManager() { return contextManager; }
    public Path projectRoot() { return projectRoot; }

    public void rebind(String newSessionId) throws IOException {
        SessionPaths next = new SessionPaths(projectRoot, newSessionId);
        next.ensureDirectories();
        this.sessionId = newSessionId;
        this.paths = next;
    }
}
```

- [ ] **Step 3: Layer1Offloader 改为持有 SessionHandle**

将字段 `SessionPaths sessionPaths` 改为 `SessionHandle handle`，所有 `sessionPaths.xxx` 改为 `handle.paths().xxx`。更新构造与 `ContextBootstrap` / `CompactionService` 中创建处。

- [ ] **Step 4: ContextBootstrap 返回 SessionHandle**

```java
public static SessionHandle create(...) throws IOException {
    String sessionId = SessionIdGenerator.generate();
    SessionPaths paths = new SessionPaths(projectRoot, sessionId);
    paths.ensureDirectories();
    // ... 组装 DefaultContextManager 时传入 SessionHandle 前先创建 handle 占位：
    // 可用两段构造：先 paths，再 manager，再 new SessionHandle(...)
    // Layer1 需要 handle：可先 new SessionHandle(projectRoot, sessionId, paths, null) 再 setContextManager，
    // 或让 Layer1 仍临时收 SessionPaths，Bootstrap 末尾用同一 paths 建 Handle——但 resume 要求 Layer1 跟 handle。
    // 推荐：SessionHandle 先创建（contextManager 稍后 set），或 Layer1 只保存 Supplier<SessionPaths> = handle::paths。
}
```

推荐最终形态：`Layer1Offloader(SessionManager, Supplier<SessionPaths> pathsSupplier, ReplacementLedger)`，Bootstrap 传入 `handle::paths`。

```java
SessionHandle handle = new SessionHandle(projectRoot, sessionId, paths, /* cm set below */ null);
Layer1Offloader layer1 = new Layer1Offloader(sessionManager, handle::paths, ledger);
// ... build manager ...
handle = handle.withContextManager(manager); // 或可变 setContextManager
return handle;
```

为简单起见可用可变 `setContextManager`：

```java
public void setContextManager(ContextManager cm) { this.contextManager = cm; }
```

（字段改为非 final。）

- [ ] **Step 5: 更新 LavenderCodeSessionInitTest**

```java
SessionHandle handle = ContextBootstrap.create(...);
assertThat(handle.contextManager()).isNotNull();
assertThat(Files.exists(projectRoot.resolve(".lavendercode/sessions"))).isTrue();
assertThat(SessionIdGenerator.isNewFormat(handle.sessionId())).isTrue();
```

全局搜索 `ContextBootstrap.create` 并适配。

- [ ] **Step 6: 运行相关测试**

Run: `mvn -q -Dtest=SessionHandleTest,LavenderCodeSessionInitTest,Layer1OffloaderTest,DefaultContextManagerTest,CompactionServiceTest test`  
Expected: PASS（按实际存在的测试类名调整）

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/lavendercode/core/context/ src/test/java/com/lavendercode/
git commit -m "功能(上下文): 引入可切换路径的 SessionHandle"
```

---

### Task 3: SessionTranscriptWriter

**Files:**
- Create: `src/main/java/com/lavendercode/chat/session/SessionTranscriptWriter.java`
- Create: `src/test/java/com/lavendercode/chat/session/SessionTranscriptWriterTest.java`

- [ ] **Step 1: 写失败测试**

```java
@TempDir Path dir;

@Test
void appendsMessageLineWithRoleContentTs() throws Exception {
    Path file = dir.resolve("conversation.jsonl");
    try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
        w.appendMessage(Role.USER, "hi", null, null, "gpt-test");
    }
    String line = Files.readString(file).trim();
    JsonNode n = new ObjectMapper().readTree(line);
    assertThat(n.get("role").asText()).isEqualTo("user");
    assertThat(n.get("content").asText()).isEqualTo("hi");
    assertThat(n.get("ts").isNumber()).isTrue();
    assertThat(n.get("model").asText()).isEqualTo("gpt-test");
}

@Test
void appendsCompactMarker() throws Exception {
    Path file = dir.resolve("conversation.jsonl");
    try (SessionTranscriptWriter w = SessionTranscriptWriter.open(file)) {
        w.appendCompactMarker();
    }
    JsonNode n = new ObjectMapper().readTree(Files.readString(file).trim());
    assertThat(n.get("type").asText()).isEqualTo("compact");
    assertThat(n.has("ts")).isTrue();
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=SessionTranscriptWriterTest test`  
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 Writer**

```java
public final class SessionTranscriptWriter implements Closeable {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();
    private final BufferedWriter writer;
    private final FileChannel channel; // 或 FileOutputStream 以便 sync

    public static SessionTranscriptWriter open(Path jsonl) throws IOException {
        Files.createDirectories(jsonl.getParent());
        // APPEND + CREATE；用 OutputStreamWriter(Files.newOutputStream(jsonl, CREATE, APPEND, WRITE))
        ...
    }

    public void appendMessage(Role role, String content,
                              List<ToolCall> toolCalls, List<ToolResult> toolResults,
                              String modelOrNull) {
        lock.lock();
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", role.name().toLowerCase()); // USER -> 需映射为 "user"
            // Role 枚举若是 USER/ASSISTANT/TOOL，用显式映射：
            // user/assistant/tool
            if (content != null) node.put("content", content);
            if (toolCalls != null && !toolCalls.isEmpty()) node.set("tool_calls", mapper.valueToTree(toolCalls));
            if (toolResults != null && !toolResults.isEmpty()) node.set("tool_results", mapper.valueToTree(toolResults));
            node.put("ts", Instant.now().getEpochSecond());
            if (modelOrNull != null) node.put("model", modelOrNull);
            writer.write(mapper.writeValueAsString(node));
            writer.newLine();
            writer.flush();
            // channel.force(true) 若可得
        } catch (IOException e) {
            Logger.getLogger(...).warning("JSONL append failed: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void appendCompactMarker() { /* {"type":"compact","ts":...} */ }

    public void appendMessage(Message m, String modelOrNull) {
        appendMessage(m.role(), m.content(), m.toolCalls(), m.toolResults(), modelOrNull);
        // tool 消息另含 toolCallId：JSON 可加 "tool_call_id" 字段以便恢复
    }

    @Override public void close() throws IOException { writer.close(); }
}
```

**恢复所需：** tool 消息必须持久化 `tool_call_id`（对应 `Message.toolCallId()`）。在 JSON 中写 `"tool_call_id"`。

Role 映射用：

```java
private static String roleJson(Role r) {
    return switch (r) {
        case USER -> "user";
        case ASSISTANT -> "assistant";
        case TOOL -> "tool";
        default -> r.name().toLowerCase();
    };
}
```

- [ ] **Step 4: 测试通过并 Commit**

```bash
mvn -q -Dtest=SessionTranscriptWriterTest test
git add src/main/java/com/lavendercode/chat/session/SessionTranscriptWriter.java src/test/java/com/lavendercode/chat/session/SessionTranscriptWriterTest.java
git commit -m "功能(会话): 新增 JSONL 会话写入器 SessionTranscriptWriter"
```

---

### Task 4: PersistingSessionManager

**Files:**
- Create: `src/main/java/com/lavendercode/chat/session/PersistingSessionManager.java`
- Create: `src/test/java/com/lavendercode/chat/session/PersistingSessionManagerTest.java`

- [ ] **Step 1: 写测试**

```java
@TempDir Path dir;
ObjectMapper mapper = new ObjectMapper();

@Test
void addUserAndAssistantPersistTwoLines() throws Exception {
    Path jsonl = dir.resolve("conversation.jsonl");
    InMemorySessionManager inner = new InMemorySessionManager();
    SessionTranscriptWriter w = SessionTranscriptWriter.open(jsonl);
    PersistingSessionManager sm = new PersistingSessionManager(inner, w, "gpt-x");
    sm.addUserMessage("u");
    sm.addAssistantMessage("a");
    w.close();
    List<String> lines = Files.readAllLines(jsonl);
    assertThat(lines).hasSize(2);
    assertThat(mapper.readTree(lines.get(0)).get("model").asText()).isEqualTo("gpt-x");
    assertThat(mapper.readTree(lines.get(1)).has("model")).isFalse();
}

@Test
void replaceHistoryWritesCompactThenMessages() throws Exception {
    Path jsonl = dir.resolve("conversation.jsonl");
    ...
    sm.addUserMessage("old");
    sm.replaceHistory(List.of(new Message(Role.USER, "sum")));
    w.close();
    String all = Files.readString(jsonl);
    assertThat(all).contains("\"type\":\"compact\"");
    assertThat(all).contains("sum");
}

@Test
void suspendPersistenceSkipsDiskOnReplace() throws Exception {
    ...
    sm.suspendPersistence();
    sm.replaceHistory(List.of(new Message(Role.USER, "loaded")));
    sm.resumePersistence();
    w.close();
    assertThat(Files.readString(jsonl)).doesNotContain("loaded"); // 或文件仍空/仅旧内容
}

@Test
void clearOnlyClearsMemory() throws Exception {
    sm.addUserMessage("x");
    sm.clear();
    assertThat(sm.getMessageCount()).isZero();
    w.close();
    assertThat(Files.readString(jsonl)).contains("x"); // 磁盘保留
}
```

- [ ] **Step 2: 实现**

```java
public final class PersistingSessionManager implements SessionManager {
    private final SessionManager inner;
    private volatile SessionTranscriptWriter writer;
    private final String modelName;
    private boolean firstMessage = true;
    private boolean persist = true;

    public PersistingSessionManager(SessionManager inner, SessionTranscriptWriter writer, String modelName) {
        this.inner = inner; this.writer = writer; this.modelName = modelName;
    }

    public void suspendPersistence() { persist = false; }
    public void resumePersistence() { persist = true; }
    public void swapWriter(SessionTranscriptWriter next) throws IOException {
        if (writer != null) writer.close();
        writer = next;
        firstMessage = false; // 恢复会话不再写 model 到「逻辑首条」——仅文件历史上第一条带 model
    }

    private String modelForNext() {
        if (firstMessage) { firstMessage = false; return modelName; }
        return null;
    }

    @Override public void addUserMessage(String content) {
        inner.addUserMessage(content);
        if (persist) writer.appendMessage(Role.USER, content, null, null, modelForNext());
    }
    // addAssistantMessage 类似
    @Override public void addToolMessages(List<ToolCall> calls, List<ToolResult> results) {
        inner.addToolMessages(calls, results);
        if (!persist) return;
        // 与 InMemory 一致：先 assistant+tools，再每条 tool result
        if (calls != null && !calls.isEmpty()) {
            writer.appendMessage(Role.ASSISTANT, null, calls, null, modelForNext());
        }
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                String id = i < calls.size() ? calls.get(i).id() : "unknown";
                writer.appendToolResult(id, results.get(i)); // 或 appendMessage TOOL
            }
        }
    }
    @Override public void replaceHistory(List<Message> messages) {
        inner.replaceHistory(messages);
        if (persist) {
            writer.appendCompactMarker();
            for (Message m : messages) writer.appendMessage(m, null);
        }
    }
    @Override public void clear() { inner.clear(); /* 不删文件、不写盘 */ }
    // updateToolContent / removeLastMessages：只转 inner，不写盘
    // 委托 getHistory/getMessageCount
}
```

- [ ] **Step 3: 测试通过并 Commit**

```bash
mvn -q -Dtest=PersistingSessionManagerTest test
git add src/main/java/com/lavendercode/chat/session/PersistingSessionManager.java src/test/java/com/lavendercode/chat/session/PersistingSessionManagerTest.java
git commit -m "功能(会话): 通过 PersistingSessionManager 持久化对话"
```

---

### Task 5: 接线 main — 打开 Writer 包装 SessionManager

**Files:**
- Modify: `src/main/java/com/lavendercode/LavenderCode.java`
- Modify: 关闭钩子关闭 Writer（`TerminalChatApplication` 或 main 的 finally）

- [ ] **Step 1: 改 main（无强制失败测；用 SessionInit 扩展）**

在 `LavenderCodeSessionInitTest` 或新建集成测：Bootstrap 后包装 Persisting，addUser 后 assert jsonl 存在。

- [ ] **Step 2: main 片段**

```java
SessionManager inner = new InMemorySessionManager();
SessionHandle handle = ContextBootstrap.create(projectRoot, selectedProvider, inner, provider, config, null);
SessionTranscriptWriter writer = SessionTranscriptWriter.open(handle.paths().conversationJsonl());
SessionManager sessionManager = new PersistingSessionManager(inner, writer, selectedProvider.model());
// 把 handle + writer 传入 TerminalChatApplication 以便退出 close / resume 切换
```

注意：`ContextBootstrap` 与 Persisting 共用同一个 `inner` 实例（Layer1/压缩改的是同一份历史）。

- [ ] **Step 3: 退出时 `writer.close()`**

- [ ] **Step 4: Commit**

```bash
git commit -m "功能(会话): 启动时接入 JSONL 持久化"
```

---

## Phase 2 — 项目指令与 Assembler（AC1–AC6, AC27）

### Task 6: InstructionLoader 三层加载

**Files:**
- Create: `src/main/java/com/lavendercode/core/prompt/InstructionLoader.java`
- Create: `src/test/java/com/lavendercode/core/prompt/InstructionLoaderTest.java`

- [ ] **Step 1: 测试三层顺序与缺失静默**

```java
@TempDir Path project;
@TempDir Path fakeHome;

@Test
void loadsThreeLayersProjectRootFirst() throws Exception {
    Files.writeString(project.resolve("LAVENDERCODE.md"), "ROOT");
    Path cfg = project.resolve(".lavendercode");
    Files.createDirectories(cfg);
    Files.writeString(cfg.resolve("LAVENDERCODE.md"), "PROJECT_CFG");
    Path userDir = fakeHome.resolve(".lavendercode");
    Files.createDirectories(userDir);
    Files.writeString(userDir.resolve("LAVENDERCODE.md"), "USER");

    String text = InstructionLoader.load(project, fakeHome);
    assertThat(text).startsWith("ROOT");
    assertThat(text).contains("PROJECT_CFG");
    assertThat(text).contains("USER");
    int iRoot = text.indexOf("ROOT");
    int iCfg = text.indexOf("PROJECT_CFG");
    int iUser = text.indexOf("USER");
    assertThat(iRoot).isLessThan(iCfg);
    assertThat(iCfg).isLessThan(iUser);
}

@Test
void missingFilesSkipped() throws Exception {
    Files.writeString(project.resolve("LAVENDERCODE.md"), "ONLY");
    assertThat(InstructionLoader.load(project, fakeHome)).isEqualTo("ONLY");
}
```

`load(Path projectRoot, Path userHome)` 便于测试注入 home；生产 `load(projectRoot)` 使用 `Path.of(System.getProperty("user.home"))`。

- [ ] **Step 2: 实现扫描拼接（先不做 include）**

按设计三路径读取 UTF-8 文本，非空层用 `\n\n` 拼接。

- [ ] **Step 3: 测试通过后进入 Task 7（可同 commit 或分 commit）**

---

### Task 7: @include 展开与安全

**Files:** 同上 `InstructionLoader`

- [ ] **Step 1: 测试**

```java
@Test
void expandsIncludeOnOwnLine() throws Exception {
    Files.createDirectories(project.resolve("rules"));
    Files.writeString(project.resolve("rules/style.md"), "STYLE");
    Files.writeString(project.resolve("LAVENDERCODE.md"), "BEFORE\n@include rules/style.md\nAFTER");
    assertThat(InstructionLoader.load(project, fakeHome)).contains("STYLE");
    assertThat(InstructionLoader.load(project, fakeHome)).doesNotContain("@include rules/style.md");
}

@Test
void depthLimitEmitsWarning() throws Exception {
    // 构造 6 层链：f1 include f2 ... f6
    // assert 含 "超过最大嵌套深度"
}

@Test
void cycleEmitsWarning() throws Exception {
    Files.writeString(project.resolve("a.md"), "@include b.md\n");
    Files.writeString(project.resolve("b.md"), "@include a.md\n");
    Files.writeString(project.resolve("LAVENDERCODE.md"), "@include a.md\n");
    String t = InstructionLoader.load(project, fakeHome);
    assertThat(t).contains("检测到环路");
}

@Test
void pathEscapeEmitsWarning() throws Exception {
    Files.writeString(project.resolve("LAVENDERCODE.md"), "@include ../../outside.md\n");
    // 在 project 外写 outside 若可能；或用绝对路径跳出
    String t = InstructionLoader.load(project, fakeHome);
    assertThat(t).contains("路径超出允许范围");
}

@Test
void inlineIncludeNotExpanded() throws Exception {
    Files.writeString(project.resolve("LAVENDERCODE.md"), "see @include x.md please");
    assertThat(InstructionLoader.load(project, fakeHome)).contains("@include x.md");
}
```

- [ ] **Step 2: 实现 expand**

- 独占行匹配：`^\s*@include\s+(\S+)\s*$`
- `depth` 从 1 起；`visited` 用 `Path.toRealPath()` 或 `toAbsolutePath().normalize()`
- 边界：`resolved.startsWith(boundary)`（注意 `Path.startsWith`）
- 二进制：读前 512 字节查 `0x00`
- 警告行：`<!-- @include 超过最大嵌套深度，已跳过: <path> -->` 等（与 PRD 一致）

- [ ] **Step 3: Commit**

```bash
git commit -m "功能(提示词): 加载 LAVENDERCODE.md 并安全展开 @include"
```

---

### Task 8: SystemPromptAssembler 三参数

**Files:**
- Modify: `src/main/java/com/lavendercode/core/prompt/SystemPromptAssembler.java`
- Modify: `src/test/java/com/lavendercode/core/prompt/SystemPromptAssemblerTest.java`

- [ ] **Step 1: 扩展测试**

```java
@Test
void threeArgAddsFileInstructionsAndMemoryInOrder() {
    String r = SystemPromptAssembler.assemble("CFG", "FILE_INSTR", "MEM_INDEX");
    assertThat(r).contains("CFG");
    assertThat(r).contains("FILE_INSTR");
    assertThat(r).contains("MEM_INDEX");
    assertThat(r.indexOf("CFG")).isLessThan(r.indexOf("FILE_INSTR"));
    assertThat(r.indexOf("FILE_INSTR")).isLessThan(r.indexOf("MEM_INDEX"));
}

@Test
void blankOptionalModulesSkipped() {
    String r = SystemPromptAssembler.assemble("CFG", "  ", null);
    assertThat(r).endsWith("CFG");
    assertThat(r).doesNotContain("MEM");
}

@Test
void singleArgDelegates() {
    assertThat(SystemPromptAssembler.assemble("X"))
        .isEqualTo(SystemPromptAssembler.assemble("X", null, null));
}
```

保留旧测试：`assemble(null)` / `assemble("Be extra careful.")` 仍通过。

- [ ] **Step 2: 实现**

```java
public static String assemble(String customInstructions) {
    return assemble(customInstructions, null, null);
}

public static String assemble(String configPrompt, String fileInstructions, String memoryIndex) {
    var modules = new ArrayList<>(FIXED);
    if (notBlank(configPrompt)) modules.add(new PromptModule("custom-instructions", 8, configPrompt));
    if (notBlank(fileInstructions)) modules.add(new PromptModule("file-instructions", 9, fileInstructions));
    if (notBlank(memoryIndex)) modules.add(new PromptModule("long-term-memory", 10, memoryIndex));
    // sort + join 同现有
}
```

- [ ] **Step 3: Commit**

```bash
git commit -m "功能(提示词): 组装配置、文件指令与记忆模块"
```

---

### Task 9: 启动注入指令到 Orchestrator

**Files:**
- Modify: `LavenderCode.java`, `TerminalChatApplication.java`, `NetworkOrchestrator.java`

- [ ] **Step 1: 构造函数增加 `String fileInstructions`、`Supplier<String> memoryIndex`（或 MemoryService）**

`handleSendMessage`：

```java
String stablePrompt = SystemPromptAssembler.assemble(
    options.systemPrompt(), fileInstructions, memoryIndexSupplier.get());
```

- [ ] **Step 2: main 中**

```java
String instructions = InstructionLoader.load(projectRoot);
// memory 暂传 () -> "" 直到 Phase 4
```

- [ ] **Step 3: 手工或单测：有 LAVENDERCODE.md 时 assemble 结果含其内容（可抽 Orchestrator 测试用包可见装配方法，或只测 Assembler+Loader 组合）**

- [ ] **Step 4: Commit**

```bash
git commit -m "功能(提示词): 运行时将文件指令注入系统提示"
```

---

## Phase 3 — 恢复与清理（AC11–AC20, AC29）

### Task 10: SessionCatalog + RelativeTime

**Files:**
- Create: `SessionListItem.java`, `SessionCatalog.java`, `RelativeTime.java` + tests

- [ ] **Step 1: 测试**

```java
@Test
void listsOnlyNewFormatWithJsonlSortedByMtime(@TempDir Path root) throws Exception {
    Path sessions = root.resolve(".lavendercode/sessions");
    // 创建 newA、newB、oldFormat 目录；仅 new* 含 conversation.jsonl
    // old: 1717000000-abc123
    List<SessionListItem> items = SessionCatalog.list(sessions);
    assertThat(items).extracting(SessionListItem::sessionId)
        .allMatch(SessionIdGenerator::isNewFormat);
    // mtime 新的在前
}

@Test
void titleTruncatedTo50() { ... }

@Test
void relativeTimeExamples() {
    assertThat(RelativeTime.format(Instant.now().minus(3, ChronoUnit.HOURS), Instant.now()))
        .contains("hour");
}
```

`SessionListItem` 字段：`sessionId, title, relativeTime, model, sizeBytes, jsonlPath`。

读首条：扫描 JSONL 找第一条 `role=user` 的 content；model 从文件中第一条带 `model` 的行。

- [ ] **Step 2: 实现并 Commit**

```bash
git commit -m "功能(会话): 构建可供 /resume 使用的会话目录"
```

---

### Task 11: SessionRestorer

**Files:**
- Create: `SessionRestorer.java` + `SessionRestorerTest.java`

- [ ] **Step 1: 测试坏行 / 孤立工具 / compact / 时间跨度**

```java
@Test
void skipsBadLinesAndLoadsAfterLastCompact(@TempDir Path dir) throws Exception {
    Path f = dir.resolve("conversation.jsonl");
    Files.writeString(f, """
        {"role":"user","content":"old","ts":1}
        {"type":"compact","ts":2}
        {"role":"user","content":"new","ts":3}
        NOT_JSON
        {"role":"assistant","content":"ok","ts":4}
        """);
    List<Message> msgs = SessionRestorer.parseMessages(f);
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).content()).isEqualTo("new");
}

@Test
void truncatesOrphanToolCalls() throws Exception {
    // assistant with tool_calls, no following tool → 结果不含该 assistant
}

@Test
void appendsTimeSpanReminderWhenStale() throws Exception {
    long old = Instant.now().getEpochSecond() - 7 * 3600;
    // 末条 ts=old → restore 后多一条 user 提醒
}
```

恢复结果类型：

```java
public record RestoreResult(List<Message> messages, boolean compacted, String timeSpanReminderOrNull) {}
```

Token 超限：注入 `TokenEstimator` + `contextWindow`，若 `estimateMessages > autoCompactThreshold` 则调用 `contextManager.runCompaction(CompactTrigger.MANUAL, List.of())`；失败吞掉记日志。

时间提醒文案：

```text
[系统提示] 本会话已暂停 <duration>。部分上下文可能已过时，如需最新信息请重新读取相关文件。
```

- [ ] **Step 2: 实现 parse + restore 编排（不碰 TUI）**

- [ ] **Step 3: Commit**

```bash
git commit -m "功能(会话): 从 JSONL 安全恢复对话"
```

---

### Task 12: SessionCleanup

**Files:**
- Create: `SessionCleanup.java` + test

- [ ] **Step 1: 测试**

```java
@Test
void deletesNewFormatOlderThan30Days(@TempDir Path sessions) throws Exception {
    String oldId = LocalDateTime.now().minusDays(31).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-abcd";
    Path oldDir = sessions.resolve(oldId);
    Files.createDirectories(oldDir);
    Files.writeString(oldDir.resolve("conversation.jsonl"), "{}");
    String keepId = SessionIdGenerator.generate();
    Files.createDirectories(sessions.resolve(keepId));
    Path legacy = sessions.resolve("1717000000-abc123");
    Files.createDirectories(legacy);

    SessionCleanup.cleanupNow(sessions, Duration.ofDays(30));

    assertThat(oldDir).doesNotExist();
    assertThat(sessions.resolve(keepId)).exists();
    assertThat(legacy).exists();
}
```

解析 ID 中时间戳：`LocalDateTime.parse(id.substring(0,15), FMT)`。

- [ ] **Step 2: `startBackground(Path sessionsRoot)` 使用 `Thread.startVirtualThread(() -> cleanupNow(...))`**

- [ ] **Step 3: main 调用 `SessionCleanup.startBackground(projectRoot.resolve(".lavendercode/sessions"))`**

- [ ] **Step 4: Commit**

```bash
git commit -m "功能(会话): 启动时清理过期会话目录"
```

---

### Task 13: `/resume` 命令、Picker、互斥

**Files:**
- Create: `SessionPickerModel.java`, `SessionPicker.java`
- Modify: `InputEvent.java`, `BuiltinCommandRegistry.java`, `NetworkOrchestrator.java`, `TerminalChatApplication.java`

- [ ] **Step 1: SessionPickerModel 单测（无终端）**

```java
@Test
void filterByTitleSubstring() {
    var model = new SessionPickerModel(List.of(
        item("a", "fix auth bug"), item("b", "refactor db")));
    model.setFilter("auth");
    assertThat(model.visible()).extracting(SessionListItem::title)
        .containsExactly("fix auth bug");
}

@Test
void moveUpDownWrapsOrClamps() { ... }
```

- [ ] **Step 2: 注册命令**

`CommandType.RESUME`；`BuiltinCommandRegistry` 加 `resume`；help 文案一行。

- [ ] **Step 3: Orchestrator 互斥**

```java
private volatile boolean resuming;
private int turnCount; // Phase 4 也用；本任务可先加字段

private void handleSendMessage(...) {
    if (resuming) { safePut(system "恢复中"); return; }
    if (currentLoop != null) return;
    ...
}

case RESUME -> {
    if (currentLoop != null) {
        safePut(system "请等待当前任务完成");
        break;
    }
    handleResume();
}
```

`handleResume()` 伪代码：

```java
resuming = true;
try {
    var items = SessionCatalog.list(...);
    SessionListItem chosen = SessionPicker.pick(terminal, items); // Esc → null
    if (chosen == null) return;
    safePut(system "正在恢复…");
    var result = SessionRestorer.restore(...);
    persisting.suspendPersistence();
    persisting.replaceHistory(result.messages());
    persisting.resumePersistence();
    handle.rebind(chosen.sessionId());
    persisting.swapWriter(SessionTranscriptWriter.open(handle.paths().conversationJsonl()));
    if (result.timeSpanReminderOrNull() != null) {
        persisting.addUserMessage(result.timeSpanReminderOrNull()); // 会追加写入
    }
    safePut(system "已恢复会话 " + id + "，共 " + n + " 条消息");
} finally {
    resuming = false;
}
```

**Terminal 获取：** `SessionPicker` 需要 `Terminal`——由 `InputSystem` 发命令时 Orchestrator 无 Terminal。两种做法（择一，设计允许）：

1. **推荐：** `/resume` 在 `InputSystem` 检测到后直接调 `SessionPicker`（input 线程已有 terminal），再把 `InputEvent.ResumeSession(sessionId)` 放入队列，Orchestrator 只做恢复。  
2. 或 Orchestrator 持有 `Terminal` 引用（启动时注入）。

Plan 采用 **做法 1**：

```java
// InputEvent
record ResumeSession(String sessionId) implements InputEvent {}

// InputSystem：parse /resume → 打开 picker → offer ResumeSession 或取消则不 offer
```

Orchestrator：

```java
case InputEvent.ResumeSession(String id) -> performRestore(id);
case ExecuteCommand(RESUME) -> { /* 若仍走命令：提示应已被 InputSystem 处理 */ }
```

若 InputSystem 处理 picker，Registry 仍识别 `/resume` 以免当普通消息。

互斥：InputSystem 在 `orchestrator.isBusy()` 时不打开 picker，改为 queue 一条系统提示命令或 `ExecuteCommand` 带提示。

给 Orchestrator 加：

```java
public boolean isAgentRunning() { return currentLoop != null; }
public boolean isResuming() { return resuming; }
```

- [ ] **Step 4: 互斥测试**

`NetworkOrchestrator` 若难测，抽 `ResumeGate`：

```java
public final class ResumeGate {
    public String check(boolean agentRunning, boolean resuming) {
        if (agentRunning) return "请等待当前任务完成";
        if (resuming) return "恢复中";
        return null;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git commit -m "功能(终端): 新增 /resume 会话选择与恢复接线"
```

---

## Phase 4 — 自动笔记（AC21–AC26）

### Task 14: MemoryService 索引读写与截断

**Files:**
- Create: `core/memory/MemoryNoteType.java`, `MemoryService.java` + tests

- [ ] **Step 1: 测试**

```java
@Test
void loadIndexConcatenatesProjectThenUserAndTruncates(@TempDir Path project, @TempDir Path home) throws Exception {
    Path pMem = project.resolve(".lavendercode/memory");
    Files.createDirectories(pMem);
    Files.writeString(pMem.resolve("MEMORY.md"), "P".repeat(100));
    Path uMem = home.resolve(".lavendercode/memory");
    Files.createDirectories(uMem);
    String big = "U".repeat(30_000);
    Files.writeString(uMem.resolve("MEMORY.md"), big);
    MemoryService svc = new MemoryService(project, home);
    String idx = svc.loadIndex();
    assertThat(idx.length()).isLessThanOrEqualTo(25_000 + "(index truncated)".length() + 10);
    assertThat(idx).contains("(index truncated)");
    assertThat(idx.startsWith("P")).isTrue();
}
```

`currentIndex()` 返回缓存；`loadIndex()` 刷新缓存。

- [ ] **Step 2: 实现路径**

- 项目：`project/.lavendercode/memory/`
- 用户：`home/.lavendercode/memory/`

- [ ] **Step 3: Commit**

```bash
git commit -m "功能(记忆): 加载并截断 MEMORY.md 索引"
```

---

### Task 15: Memory 异步更新 + Complete 钩子

**Files:**
- Create: `MemoryAction.java`
- Modify: `MemoryService.java`, `NetworkOrchestrator.java`
- Create: `MemoryServiceUpdateTest.java`

- [ ] **Step 1: 更新协议测试（mock Provider）**

```java
@Test
void appliesCreateUpdateDeleteFromLlmJson(@TempDir Path project, @TempDir Path home) throws Exception {
    LlmProvider provider = mock(LlmProvider.class);
    // stub streamChat 返回一条完成 JSON 数组文本的流——按项目现有 mock 模式
    // 若 stream 难 mock，将 MemoryService 拆 parseActions(String json) + apply(actions) 单测 apply

    MemoryService svc = new MemoryService(project, home);
    List<MemoryAction> actions = MemoryService.parseActions("""
        [{"action":"create","level":"project","type":"project_knowledge",
          "title":"API","slug":"api","content":"Use REST"}]
        """);
    svc.applyActions(actions);
    assertThat(project.resolve(".lavendercode/memory/project_knowledge_api.md")).exists();
    assertThat(Files.readString(project.resolve(".lavendercode/memory/MEMORY.md")))
        .contains("project_knowledge").contains("API");
}

@Test
void updateFailureIsSwallowed() {
    // provider 抛错 → apply 不抛到调用方
}
```

`MemoryAction` record 字段对齐 Spec F39。

Frontmatter 写入：

```yaml
---
type: project_knowledge
title: API
created: <ISO_OFFSET>
updated: <ISO_OFFSET>
---
content
```

索引行：`- [project_knowledge] API — Use REST`（一句话可用 content 首行）。

触发：

```java
public boolean shouldUpdate(int turnCount, String lastUserMessage) {
    if (turnCount > 0 && turnCount % 5 == 0) return true;
    if (lastUserMessage == null) return false;
    String lower = lastUserMessage.toLowerCase();
    return lastUserMessage.contains("记住") || lastUserMessage.contains("记忆")
        || lastUserMessage.contains("别忘") || lower.contains("remember") || lower.contains("memo");
}

public void maybeUpdateAsync(int turnCount, String lastUser, List<Message> recentSnapshot, LlmProvider provider, LlmConfig config) {
    if (!shouldUpdate(turnCount, lastUser)) return;
    Thread.startVirtualThread(() -> {
        try { updateBlocking(recentSnapshot, provider, config); }
        catch (Exception e) { logger.warning(...); }
    });
}
```

更新请求：无 tools；把索引 + 最近消息拼进临时 user/assistant history 或单条 user prompt（实现时固定 prompt 模板字符串写入 `MemoryService` 常量）。

- [ ] **Step 2: Orchestrator Complete**

保留 `lastUserMessage` 字段在 `handleSendMessage` 赋值；`turnCount++` 在 `Complete`：

```java
case AgentEvent.Complete c -> {
    ...
    turnCount++;
    memoryService.maybeUpdateAsync(turnCount, lastUserMessage, snapshotRecentRound(), provider, config);
}
```

`assemble` 使用 `memoryService::currentIndex`。

- [ ] **Step 3: 异步不阻塞 —— 单测 `shouldUpdate` + 验证 `maybeUpdateAsync` 立即返回（mock 慢 provider + CountDownLatch）**

- [ ] **Step 4: Commit**

```bash
git commit -m "功能(记忆): Agent 完成后异步更新笔记"
```

---

## 收尾 Task 16: 回归与文档状态

- [ ] **Step 1: 全量测试**

Run: `mvn -q test`  
Expected: PASS（跳过需真网络的 `*IntegrationTest` 若默认已排除；否则用现有 surefire 配置）

- [ ] **Step 2: 更新设计文档状态为「已实现计划」**（可选）将 design 文首状态改为已批准并附 plan 链接

- [ ] **Step 3: Commit**（若有文档变更）

```bash
git add -f docs/superpowers/plans/2026-07-13-project-memory-session-persistence.md
git commit -m "文档: 补充记忆与会话持久化实现计划"
```

---

## Spec Coverage（自检）

| Spec / AC | Task |
|---|---|
| G8 / AC7 Session ID | T1 |
| SessionHandle / Layer1 rebind | T2 |
| F11–F16 JSONL Writer | T3 |
| F13 / AC8–10 / AC28 装饰器 | T4–T5 |
| F1–F6 / AC1–6 指令 | T6–T7 |
| F7–F8 / F43 / AC27 Assembler | T8–T9 |
| F17–F20 Catalog/Picker | T10, T13 |
| F21–F24 Restorer | T11, T13 |
| F25–F26 / AC19–20 Cleanup | T12 |
| F46 / AC29 互斥 | T13 |
| F27–F34 / AC23,26 索引 | T14 |
| F35–F42 / AC21–25 更新 | T15 |
| `/clear` 同文件 | T4 |
| 不做向量/自动恢复等 | 无任务（刻意不做） |

## Placeholder / 一致性自检

- 无 TBD；Resume 的 Terminal 归属已钉死为 InputSystem 做法 1  
- `replaceHistory` 在 resume 时用 `suspendPersistence`，避免重复 compact  
- tool 消息持久化含 `tool_call_id`  
- Assembler priority 8/9/10 与决策一致  
- 路径统一 `.lavendercode`

---
