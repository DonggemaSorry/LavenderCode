# 终端聊天界面重设计 — 详细设计

| 项目 | 内容 |
|------|------|
| 文档名称 | 终端聊天界面重设计 |
| 设计版本 | v1.0 |
| 状态 | 已确认 |
| 日期 | 2026-06-19 |
| 关联 PRD | `docs/current/modules/terminal-ui/PRD_terminal-chat-interface.md` |
| 存放路径 | `docs/superpowers/specs/2026-06-19-terminal-chat-redesign-design.md` |

---

## 一、总体架构

```
┌──────────┐      ┌──────────────┐      ┌────────────┐      ┌──────────┐
│InputThread│      │NetworkThread │      │ I/O 线程池  │      │RenderThread│
│          │      │  (编排/调度)   │      │ (每次请求    │      │          │
│ LineReader│      │              │      │  一个线程)   │      │ Terminal │
│          │─────→│ 消费InputQueue│─────→│            │─────→│          │
│          │      │ 分发任务       │      │ 读SSE流     │      │ 消费      │
│          │      │ 永不阻塞       │      │ 写DeltaBuffer│     │RenderQueue│
└──────────┘      └──────────────┘      └────────────┘      └──────────┘
   键盘输入         InputQueue            ExecutorService       RenderQueue
```

| 线程 | 核心职责 | 唯一阻塞点 |
|------|----------|-----------|
| InputThread | JLine3 LineReader.readLine() → InputQueue | JLine3 等待按键（合理） |
| NetworkThread | 消费 InputQueue、编排任务、命令处理 | 无阻塞（仅 `poll`） |
| I/O 线程池 | 消费 OkHttp SSE 流 → DeltaBuffer | HTTP 响应流读取（隔离） |
| RenderThread | 消费 RenderQueue → 终端绘制 | `RenderQueue.take()` |

**两个线程安全队列：**

| 队列 | 类型 | 生产者 | 消费者 |
|------|------|--------|--------|
| `InputQueue` | `LinkedBlockingQueue<InputEvent>` | InputThread | NetworkThread |
| `RenderQueue` | `LinkedBlockingQueue<RenderEvent>` | NetworkThread + I/O 线程 | RenderThread |

---

## 二、事件系统

### 2.1 InputEvent

```java
sealed interface InputEvent
    permits InputEvent.SendMessage,
            InputEvent.ExecuteCommand,
            InputEvent.Shutdown {

    record SendMessage(String text) implements InputEvent {}
    record ExecuteCommand(CommandType type, String args) implements InputEvent {}
    record Shutdown() implements InputEvent {}
}

enum CommandType { EXIT, QUIT, CLEAR, HELP, THEME, CANCEL, SCROLL }
```

### 2.2 RenderEvent

```java
sealed interface RenderEvent
    permits RenderEvent.AppendToMessage,
            RenderEvent.FinalizeMessage,
            RenderEvent.AddUserMessage,
            RenderEvent.AddSystemMessage,
            RenderEvent.ThinkDelta,
            RenderEvent.ScrollTo,
            RenderEvent.ScrollAutoReset,
            RenderEvent.ClearChat,
            RenderEvent.WindowResize,
            RenderEvent.ThemeChange,
            RenderEvent.StatusUpdate,
            RenderEvent.RefreshAll,
            RenderEvent.Shutdown {

    record AppendToMessage(String text) implements RenderEvent {}
    record FinalizeMessage() implements RenderEvent {}
    record AddUserMessage(String text) implements RenderEvent {}
    record AddSystemMessage(String text) implements RenderEvent {}
    record ThinkDelta(String text) implements RenderEvent {}
    record ScrollTo(int lineIndex) implements RenderEvent {}
    record ScrollDelta(int offset) implements RenderEvent {}
    record ScrollAutoReset() implements RenderEvent {}
    record ClearChat() implements RenderEvent {}
    record WindowResize(int cols, int rows) implements RenderEvent {}
    record ThemeChange(Theme theme) implements RenderEvent {}
    record StatusUpdate(String model, int tokenCount, boolean isEstimating) implements RenderEvent {}
    record RefreshAll() implements RenderEvent {}
    record Shutdown() implements RenderEvent {}
}
```

### 2.3 DeltaEvent — ChatService 输出

```java
sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.Thinking,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {

    record Content(String text) implements DeltaEvent {}
    record Thinking(String text) implements DeltaEvent {}
    record Complete() implements DeltaEvent {}
    record Error(String message, int statusCode) implements DeltaEvent {}
    record Usage(int inputTokens, int outputTokens) implements DeltaEvent {}
}
```

---

## 三、DeltaBuffer — 事件缓冲与合并

### 3.1 设计目标

- NetworkThread 永不阻塞，使用 `ScheduledExecutorService` 调度定时刷新
- 合并窗口 50ms，所有 Delta 类型共用同一缓冲区
- `forceFlush()` 立即排空，且自旋排空清空期间新到达的事件
- `putAll` 阻塞期间不丢失新事件（快照 + 立即清空）

### 3.2 实现

```java
class DeltaBuffer {
    private final List<BufferedEvent> events = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;
    private volatile boolean flushing = false;
    private final Object lock = new Object();
    private final ScheduledExecutorService timerScheduler;
    private final RenderQueue renderQueue;

    // ========== 追加（I/O 线程调用，非阻塞）==========
    void append(BufferedEvent event) {
        synchronized (lock) {
            events.add(event);
            scheduleIfNeeded();
        }
    }

    // ========== 强制排空 — 自旋等待 + 反复清空 ==========
    void forceFlush() {
        cancelTimer();
        doFlush();
        while (hasPending()) {
            cancelTimer();
            doFlush();
        }
    }

    private boolean hasPending() {
        synchronized (lock) { return !events.isEmpty(); }
    }

    // ========== 核心排空 ==========
    private void doFlush() {
        List<BufferedEvent> snapshot;
        synchronized (lock) {                          // 锁 A 开始
            if (flushing) return;
            flushing = true;
            snapshot = new ArrayList<>(events);        // 快照复制
            events.clear();                            // 立即清空
        }                                              // 锁 A 结束

        try {
            List<RenderEvent> batch = buildBatch(snapshot);
            if (!batch.isEmpty()) {
                renderQueue.putAll(batch);             // 阻塞在此，不碰 events
            }
        } finally {
            flushing = false;
        }
    }

    // ========== 快照 → 合并后的 RenderEvent 列表 ==========
    private List<RenderEvent> buildBatch(List<BufferedEvent> snapshot) {
        List<RenderEvent> result = new ArrayList<>();
        StringBuilder textBuf  = new StringBuilder();
        StringBuilder thinkBuf = new StringBuilder();
        Type lastType = null;

        for (BufferedEvent e : snapshot) {
            if (e.type != lastType && lastType != null) {
                flushBuffer(result, lastType, textBuf, thinkBuf);
            }
            switch (e.type) {
                case CONTENT_DELTA     -> textBuf.append(e.text);
                case THINK_DELTA       -> thinkBuf.append(e.text);
                case STREAM_COMPLETE,
                     STREAM_ERROR,
                     USER_MESSAGE,
                     SYSTEM_MESSAGE    -> {
                        flushBuffer(result, lastType, textBuf, thinkBuf);
                        result.add(e.toRenderEvent());
                     }
            }
            lastType = e.type;
        }
        flushBuffer(result, lastType, textBuf, thinkBuf);
        return result;
    }

    private void flushBuffer(List<RenderEvent> result, Type type,
                             StringBuilder textBuf, StringBuilder thinkBuf) {
        if (type == CONTENT_DELTA && textBuf.length() > 0) {
            result.add(new AppendToMessage(textBuf.toString()));
            textBuf.setLength(0);
        }
        if (type == THINK_DELTA && thinkBuf.length() > 0) {
            result.add(new ThinkDelta(thinkBuf.toString()));
            thinkBuf.setLength(0);
        }
    }
}
```

### 3.3 操作触发规则

| 触发条件 | 行为 |
|----------|------|
| 任意 Delta 到达 | 追加到 `events`；若无待发定时器则 schedule 50ms |
| 50ms 定时器到期 | `doFlush()` 排空 |
| StreamComplete / StreamError | `forceFlush()` 先排空缓冲，再投递 Complete/Error |
| SendMessage / ExecuteCommand | `forceFlush()` 先排空气 AI 输出 |
| Shutdown | `forceFlush()` 排空后投递 Shutdown |

---

## 四、NetworkOrchestrator — 编排调度

```java
class NetworkOrchestrator {
    private final AtomicReference<RequestContext> currentRequest = new AtomicReference<>();
    private final ChatService chatService;
    private final DeltaBuffer deltaBuffer;
    private final RenderQueue renderQueue;
    private final InputQueue inputQueue;

    void run() {
        while (true) {
            InputEvent event = inputQueue.poll(100, MILLISECONDS);
            if (event == null) continue;

            switch (event) {
                case SendMessage msg -> handleSendMessage(msg);
                case ExecuteCommand cmd -> handleCommand(cmd);
                case Shutdown -> {
                    RequestContext ctx = currentRequest.getAndSet(null);
                    if (ctx != null) chatService.cancel(ctx);
                    deltaBuffer.forceFlush();
                    renderQueue.put(new RenderEvent.Shutdown());
                    return;
                }
            }
        }
    }

    private void handleSendMessage(SendMessage msg) {
        deltaBuffer.forceFlush();
        renderQueue.put(new AddUserMessage(msg.text));

        var ctxRef = new AtomicReference<RequestContext>();
        try {
            ctxRef.set(chatService.submit(
                provider, msg.history, msg.config,
                delta -> onDeltaReceived(ctxRef.get(), delta)
            ));
            currentRequest.set(ctxRef.get());
        } catch (Exception e) {
            currentRequest.set(null);           // ← 清空残留引用
            deltaBuffer.forceFlush();
            renderQueue.put(new AddSystemMessage("[Error] " + e.getMessage()));
            renderQueue.put(new FinalizeMessage());
        }
    }

    private void handleCommand(ExecuteCommand cmd) {
        switch (cmd.type()) {
            case CANCEL -> {
                RequestContext ctx = currentRequest.getAndSet(null);
                if (ctx != null) {
                    chatService.cancel(ctx);
                    // 主动完成 UI 收尾
                    deltaBuffer.forceFlush();
                    renderQueue.put(new AddSystemMessage("[Cancelled]"));
                    renderQueue.put(new FinalizeMessage());
                }
            }
            case CLEAR -> {
                deltaBuffer.forceFlush();
                renderQueue.put(new ClearChat());
            }
            case THEME -> {
                deltaBuffer.forceFlush();
                renderQueue.put(new ThemeChange(resolveTheme(cmd.args())));
            }
            case EXIT, QUIT -> {
                RequestContext ctx = currentRequest.getAndSet(null);
                if (ctx != null) chatService.cancel(ctx);
                deltaBuffer.forceFlush();
                renderQueue.put(new RenderEvent.Shutdown());
            }
            case HELP -> {
                deltaBuffer.forceFlush();
                renderQueue.put(new AddSystemMessage(helpText));
            }
            case SCROLL -> {
                deltaBuffer.forceFlush();
                renderQueue.put(parseScrollEvent(cmd.args()));
            }
        }
    }

    // SCROLL 命令示例: /scroll up → ScrollDelta(-1); /scroll top → ScrollTo(0)
    private RenderEvent parseScrollEvent(String args) {
        return switch (args.trim().toLowerCase()) {
            case "up"        -> new RenderEvent.ScrollDelta(-1);
            case "down"      -> new RenderEvent.ScrollDelta(1);
            case "page-up"   -> new RenderEvent.ScrollDelta(-viewportHeight);
            case "page-down" -> new RenderEvent.ScrollDelta(viewportHeight);
            case "top"       -> new ScrollTo(0);
            case "bottom"    -> new ScrollAutoReset();
            default          -> null;  // 不投递
        };
    }

    // I/O 线程回调 — 身份校验
    private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
        if (currentRequest.get() != ctx) return;  // ← 身份校验

        switch (delta) {
            case Content(String t)  -> deltaBuffer.append(Buffered(CONTENT, t));
            case Thinking(String t) -> deltaBuffer.append(Buffered(THINK, t));
            case Usage(int i, int o)-> renderQueue.put(new StatusUpdate(model, i + o, false));
            case Complete() -> {
                if (currentRequest.compareAndSet(ctx, null)) {  // CAS 防止竞态
                    deltaBuffer.forceFlush();
                    renderQueue.put(new FinalizeMessage());
                }
            }
            case Error(String m, int c) -> {
                if (currentRequest.compareAndSet(ctx, null)) {
                    deltaBuffer.forceFlush();
                    renderQueue.put(new AddSystemMessage("[Error] " + m));
                    renderQueue.put(new FinalizeMessage());
                }
            }
        }
    }
}
```

---

## 五、ChatService — Provider 集成

### 5.1 接口

```java
interface ChatService {
    RequestContext submit(LlmProvider provider,
                          List<Message> history,
                          LlmConfig config,
                          Consumer<DeltaEvent> onDelta);
    void cancel(RequestContext ctx);
}
```

### 5.2 RequestContext — 每次请求隔离

```java
class RequestContext {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Call httpCall;
    private final StreamEventIterator iterator;

    boolean isCancelled() { return cancelled.get(); }

    void cancel() {
        cancelled.set(true);
        httpCall.cancel();
        iterator.close();
    }
}
```

### 5.3 StreamingChatService — 实现

```java
class StreamingChatService implements ChatService {
    private final ExecutorService ioPool = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient;

    @Override
    public RequestContext submit(LlmProvider provider, List<Message> history,
                                 LlmConfig config, Consumer<DeltaEvent> onDelta) {
        StreamEventIterator iterator = provider.streamChat(history, config);
        RequestContext ctx = new RequestContext(iterator.getCall(), iterator);

        ioPool.submit(() -> {
            try {
                while (iterator.hasNext() && !ctx.isCancelled()) {
                    StreamEvent se = iterator.next();
                    if (ctx.isCancelled()) break;
                    DeltaEvent de = toDeltaEvent(se);
                    if (de != null) onDelta.accept(de);
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
}
```

---

## 六、渲染引擎

### 6.1 核心结构

```java
class TerminalRenderer {
    private final Terminal terminal;
    private final List<MessageBlock> blocks;
    private MessageBlock currentAIBlock;
    private int viewportStart;
    private boolean autoScroll = true;
    private Theme theme;
    private StatusLine status;

    private static final int STATUS_HEIGHT = 1;
    private static final int INPUT_HEIGHT = 3;
}
```

### 6.2 渲染主循环

```java
void run() {
    registerResizeHandler();
    terminal.enterRawMode();

    while (true) {
        RenderEvent event = renderQueue.take();
        if (event instanceof Shutdown) break;

        switch (event) {
            case AppendToMessage(var text) -> appendToAIBlock(text);
            case FinalizeMessage()         -> finalizeBlock();
            case AddUserMessage(var text)  -> addUserBlock(text);
            case AddSystemMessage(var text)-> addSystemBlock(text);
            case ThinkDelta(var text)      -> appendThinking(text);
            case ClearChat()               -> { blocks.clear(); currentAIBlock = null; drawFull(); }
            case ScrollTo(int n)           -> { viewportStart = clamp(n); autoScroll = false; drawDiff(STATUS_HEIGHT, viewportHeight); }
            case ScrollDelta(int d)         -> scrollDelta(d);
            case ScrollAutoReset()         -> { autoScroll = true; scrollToBottom(); drawDiff(STATUS_HEIGHT, viewportHeight); }
            case WindowResize(int c, int r)-> { reflowAll(); drawFull(); }
            case ThemeChange(var t)        -> { this.theme = t; updateStatusTheme(); drawFull(); }
            case StatusUpdate(var m, var c)-> { updateStatus(m, c); drawStatusBar(); }
            case RefreshAll()              -> drawFull();
        }
    }

    terminal.close();
}
```

### 6.3 两种绘制函数

**`drawFull()`**：清屏 → 状态栏 → 遍历视口逐行绘制 → 滚动条 → 恢复光标
**`drawDiff(firstRow, count)`**：仅重绘终端第 firstRow 到 firstRow+count 行（含滚动条）

| 场景 | 使用函数 |
|------|----------|
| ClearChat / ThemeChange / WindowResize / RefreshAll | `drawFull()` |
| AppendToMessage / AddUserMessage / AddSystemMessage | `drawDiff()` — 只重绘受影响的若干行 |
| ScrollDelta | `drawDiff(STATUS_HEIGHT, viewportHeight)` — 滚动时全视口重绘 |

`drawDiff` 内部统一包含该行右侧的滚动条指示，调用一次解决。

### 6.4 Append 处理 — 防御空指针

```java
void appendToAIBlock(String text) {
    if (currentAIBlock == null) {
        currentAIBlock = new MessageBlock(ASSISTANT, false);
        blocks.add(currentAIBlock);
    }

    int oldLineCount = currentAIBlock.lineCount();
    currentAIBlock.append(text);                    // 逻辑行追加 + 终端折行
    int newLineCount = currentAIBlock.lineCount();
    int added = newLineCount - oldLineCount;

    int firstChangedRow = blockToScreenRow(currentAIBlock) + oldLineCount;
    drawDiff(firstChangedRow, added + 1);

    if (autoScroll) {
        scrollToBottom();
    }
}
```

### 6.5 键盘滚动路由

渲染模式下，LineReader 由 InputThread 独占。用户滚动操作通过 JLine3 LineReader 的 Widget 机制拦截按键，生成 `InputEvent.ExecuteCommand(SCROLL, direction)` 提交到 InputQueue 实现。

**按键 → 滚动事件映射：**

| 按键 | 生成事件 |
|------|----------|
| `Ctrl+↑` | `ExecuteCommand(SCROLL, "up")` → `ScrollDelta(-1)` |
| `Ctrl+↓` | `ExecuteCommand(SCROLL, "down")` → `ScrollDelta(1)` |
| `PageUp` | `ExecuteCommand(SCROLL, "page-up")` → `ScrollDelta(-viewportHeight)` |
| `PageDown` | `ExecuteCommand(SCROLL, "page-down")` → `ScrollDelta(viewportHeight)` |
| `Ctrl+Home` | `ExecuteCommand(SCROLL, "top")` → `ScrollTo(0)` |
| `Ctrl+End` | `ExecuteCommand(SCROLL, "bottom")` → `ScrollAutoReset` |

按键绑定在 LineReader KeyMap 中注册，拦截这些组合键后将生成的输入文本替换为 `/scroll <direction>` 命令，InputThread 无需特殊处理。

### 6.6 视口边界校验

```java
void clampViewport() {
    int maxStart = Math.max(0, totalContentLines() - viewportHeight);
    viewportStart = Math.max(0, Math.min(viewportStart, maxStart));
}

void scrollDelta(int delta) {
    int oldStart = viewportStart;
    viewportStart += delta;
    clampViewport();

    if (viewportStart != oldStart) {
        drawDiff(STATUS_HEIGHT, viewportHeight);       // 全视口重绘
        if (viewportStart == maxViewportStart()) {
            autoScroll = true;
        }
    }
}
```

---

### 6.7 辅助方法语义

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `totalContentLines()` | `int` | 所有 `blocks` 中全部行的总数 = `Σ blocks[i].lineCount()` |
| `blockToScreenRow(block)` | `int` | 该 block 的第一行在全部行中的索引 = 前面所有 block 的 `lineCount()` 之和 |
| `reflowAll()` | `void` | 遍历所有 blocks，对每个 block 按新 `terminal.width` 重新折行；不改变 `viewportStart`（若视口超出新总行数边界则 `clampViewport()`） |
| `maxViewportStart()` | `int` | `max(0, totalContentLines() - viewportHeight)` |
| `scrollToBottom()` | `void` | `viewportStart = maxViewportStart()` |

---

## 七、消息区数据结构

### 7.1 MessageBlock

```java
class MessageBlock {
    private final UUID id;
    private final Role role;
    private boolean isComplete;
    private final List<Segment> segments;         // thinking 与 content 按到达顺序交错

    sealed interface Segment {
        record ThinkingText(List<RenderedLine> lines) implements Segment {}
        record ContentText(List<RenderedLine> lines)  implements Segment {}
    }

    void append(String text) {
        // 扫描三个反引号围栏，切换 inCodeBlock 状态
        // 追加到最后一个 ContentSegment 的原始文本 → 重新折行
    }

    void appendThinking(String text) {
        // 追加到最后一个 ThinkingSegment，或新建
    }

    void markComplete() { isComplete = true; }

    int lineCount() {
        return segments.stream().flatMapToInt(s -> s.lines().size()).sum();
    }
}
```

### 7.2 折行逻辑

```
收到 delta " World"（追加到当前逻辑行末尾）：

之前：
  逻辑行缓冲区: "Hello..." (78字符)
  lines: [..., [N] "Hello..." (78/80)]

之后：
  逻辑行缓冲区: "Hello... World" (84字符)
  lines: [..., [N] "Hello... World..." (80/80), [N+1] "ld" (4/80)]
```

流程：
1. 将 delta 拼接到最后一个逻辑行原始文本末尾
2. 对该逻辑行按 `terminal.width` 重新折行
3. 用新折行结果替换 lines 末尾旧行 + 追加溢出新行
4. 返回 affected 行数

### 7.3 Thinking 的视觉归属

思考内容归属到当前 AI 消息块，在 AI 回复正文之前以缩进块呈现：

```
┌─ AI ──────────────────────────────────────┐
│ [Thinking]                                 │
│   分析用户意图...                         ← 缩进，灰/斜体
│ [Thinking]                                 │
│                                           │
│ 根据分析，答案是：...                     ← 正文紧随
└───────────────────────────────────────────┘
```

---

## 八、输入系统

### 8.1 JLine3 LineReader 配置

```java
class InputSystem {
    private final LineReader reader;
    private final InputQueue inputQueue;

    InputSystem(Terminal terminal, InputQueue inputQueue) {
        LineReaderBuilder builder = LineReaderBuilder.builder()
            .terminal(terminal)
            .variable(LineReader.HISTORY_FILE, historyPath)
            .variable(LineReader.HISTORY_SIZE, 1000)
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P  ")
            .completer(new CommandCompleter());

        this.reader = builder.build();
        configureKeyBindings(reader);
    }
}
```

### 8.2 按键绑定

| 按键 | 绑定 | 行为 |
|------|------|------|
| `Enter` | `ACCEPT_LINE`（默认） | 提交整段文本 |
| `Alt+Enter` | `KeyMap.alt(KeyMap.ctrl('J'))` | 插入换行 |
| `Ctrl+C` | 默认抛 `UserInterruptException` | catch 后发 Cancel 事件 |
| `Ctrl+D`（空行） | 抛 `EndOfFileException` | catch 后发 Shutdown |
| `↑` `↓` | 默认行为 | 浏览历史命令 |
| `Tab` | `CommandCompleter` | 补全 `/` 命令 |

### 8.3 输入主循环

```java
void run() {
    while (!shutdown.get()) {
        String line;
        try {
            line = reader.readLine("> ");
        } catch (UserInterruptException e) {
            inputQueue.put(new ExecuteCommand(CANCEL, ""));
            continue;
        } catch (EndOfFileException e) {
            inputQueue.put(new InputEvent.Shutdown());
            break;
        }

        if (line == null) {
            inputQueue.put(new InputEvent.Shutdown());
            break;
        }

        if (line.trim().isEmpty()) continue;
        inputQueue.put(line.startsWith("/")
            ? new ExecuteCommand(parseCommandType(line), parseArgs(line))
            : new SendMessage(line));
    }
}
```

---

## 九、主题系统

### 9.1 StyleCatalog

```java
enum StyleCatalog {
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

### 9.2 Theme

```java
record Theme(String name, Map<StyleCatalog, AttributedStyle> styles) {
    AttributedStyle get(StyleCatalog key) { return styles.get(key); }
}
```

渲染代码只引用 `StyleCatalog` 枚举，不感知具体颜色值。

### 9.3 预置主题

- **Dark**：暗色背景 + 亮色文字，代码块深灰底（`AnsiColor.of(8)`）
- **Light**：浅色背景 + 深色文字，代码块浅灰底

使用 JLine3 AnsiColor 16 色确保最大终端兼容性。

### 9.4 主题切换流程

```
用户输入 /theme light
  → InputThread → InputQueue → NetworkThread
    → RenderQueue.put(ThemeChange(lightTheme))
    → RenderThread: theme = lightTheme → drawFull()
```

---

## 十、文件布局

### 10.1 新增文件（`com.lavendercode.chat.terminal`）

```
src/main/java/com/lavendercode/chat/terminal/
├── TerminalChatApplication.java    // 入口编排
├── InputSystem.java                // JLine3 LineReader + 按键绑定
├── NetworkOrchestrator.java        // 消费 InputQueue，分发到 ChatService
├── DeltaBuffer.java                // 事件缓冲 + 合并 + 定时排空
├── ChatService.java                // 接口
├── StreamingChatService.java       // 实现：Provider → DeltaEvent 流
├── RequestContext.java             // 请求隔离上下文
├── TerminalRenderer.java           // 消费 RenderQueue → 终端绘制
├── MessageBlock.java               // 消息块 + Segment + 行缓存
├── RenderEvent.java                // 渲染事件
├── InputEvent.java                 // 输入事件
├── DeltaEvent.java                 // ChatService 输出事件
├── StyleCatalog.java               // 语义化样式枚举
├── Theme.java                      // 主题 record + 工厂
└── RenderedLine.java               // 已折行的终端行
```

### 10.2 修改文件

| 文件 | 改动 |
|------|------|
| `LavenderCode.java` | 删除 Lanterna Swing 代码；改为启动 `TerminalChatApplication` |
| `pom.xml` | 删除 `lanterna` 依赖；新增 `org.jline:jline:3.26+` 依赖 |
| `TuiApplication.java` | 删除（整体替换） |

### 10.3 不变文件

`core/provider/*`、`core/config/*`、`chat/session/*`、`core/sse/*` — 核心业务逻辑不受影响。

### 10.4 依赖方向（单向，无循环）

```
LavenderCode
  → TerminalChatApplication
      → InputSystem         (依赖 JLine3, InputQueue<InputEvent>)
      → NetworkOrchestrator  (依赖 InputQueue, DeltaBuffer, ChatService)
      → TerminalRenderer     (依赖 RenderQueue<RenderEvent>, Terminal, Theme)
```

---

## 十一、迁移策略与风险控制

### 11.1 四阶段迁移

| 阶段 | 内容 | 回退方式 |
|------|------|----------|
| 1 — 基础设施 | pom.xml 双库并存；创建事件枚举 + DeltaBuffer + StyleCatalog + Theme；`mvn test` 验证 | `git checkout` 不影响的文件 |
| 2 — 新引擎 | 实现 TerminalRenderer + InputSystem + NetworkOrchestrator + ChatService；集成测试 mock Provider | 新旧并存，无风险 |
| 3 — 切换入口 | CLI 参数 `--ui=v1`/`v2`（默认 v2）；手动验证完整流程 | `--ui=v1` 运行时即时回退 |
| 4 — 清理 | 删除 Lanterna + TuiApplication + Swing 代码 + 分支代码；更新测试 | 前三个阶段已验证稳定 |

### 11.2 风险矩阵

| 风险 | 概率 | 影响 | 控制 |
|------|------|------|------|
| JLine3 在非 Windows Terminal 终端不兼容 | 中 | 高：无法启动 | 阶段 3 保留 `--ui=v1` 降级 |
| SSE 解析时序错误 | 中 | 中：显示乱序 | DeltaBuffer 单元测试全覆盖 |
| OkHttp + ioPool 双重线程池泄漏 | 低 | 高：OOM | try-with-resources + jmap 验证 |
| 长对话（>200 轮）内存膨胀 | 低 | 中：GC 卡顿 | 配置上限 500 轮，超出修剪 |
| ANSI 颜色不同终端渲染差异 | 中 | 低：视觉回退 | 16 色基线，非 256 色 |

### 11.3 测试策略

| 层级 | 范围 | 技术 |
|------|------|------|
| 单元测试 | DeltaBuffer、MessageBlock 折行、Theme 映射 | JUnit 5，无终端依赖 |
| 集成测试 | InputEvent → NetworkOrchestrator → TerminalRenderer | JLine3 `DumbTerminal` + Mock Provider |
| 端到端 | 真实 Terminal + 真实 API | 手动验收（PRD V-001 ~ V-008 + resize + /cancel） |

---

## 十二、验收清单

| 编号 | 验收项 | 来源 |
|------|--------|------|
| AC-01 | 真实终端窗口运行（非 Swing 弹窗） | PRD 页面结构 |
| AC-02 | AI 回复流式逐字输出，原地刷新同一消息块 | PRD 注意事项 §1 |
| AC-03 | 代码块以不同背景色高亮显示 | PRD 注意事项 §2 |
| AC-04 | 状态栏实时更新 Token 计数 | PRD 页面结构 |
| AC-05 | ↑↓ PageUp/PageDown Home/End 精确滚动 | PRD 枚举 |
| AC-06 | 窗口 resize 后消息自动重新排版，视口位置稳定 | PRD 注意事项 §6 |
| AC-07 | `/theme light` `/theme dark` 即时切换 | PRD 枚举 |
| AC-08 | 长消息自动换行，不截断 | PRD 注意事项 §3 |
| AC-09 | Enter 提交，Alt+Enter 换行 | PRD 流程图 |
| AC-10 | `/cancel` 后 UI 正确收尾，不卡在"处理中" | — |
| AC-11 | 100+ 轮对话滚动/重绘无卡顿 | PRD 注意事项 §10 |
| AC-12 | Ctrl+C 中断当前请求，不退出程序 | — |
