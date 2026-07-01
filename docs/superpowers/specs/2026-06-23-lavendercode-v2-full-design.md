# LavenderCode v2 全功能详细设计

| 项目 | 内容 |
|------|------|
| 文档名称 | LavenderCode v2 全功能详细设计 |
| 设计版本 | v1.0 |
| 状态 | 未确认 |
| 日期 | 2026-06-23 |
| 关联 PRD | `docs/current/modules/LavenderCode/PRD_v2-full-requirements.md` |
| 存放路径 | `docs/superpowers/specs/2026-06-23-lavendercode-v2-full-design.md` |

---

## 一、总体架构

### 1.1 架构继承

保留现有四线程架构不变：

```
┌──────────┐      ┌──────────────┐      ┌──────────────┐      ┌──────────┐
│ lavender- │      │ lavender-    │      │ lavender-io   │      │ lavender-│
│ input     │      │ network      │      │ (CachedPool)  │      │ render   │
│          │─────→│              │─────→│              │─────→│          │
│InputSystem│     │ Network      │      │StreamingChat │      │ Terminal │
│TerminalKey│      │ Orchestrator │      │ Service      │      │ Renderer │
│ Reader    │      │              │      │              │      │          │
└──────────┘      └──────────────┘      └──────────────┘      └──────────┘
  InputQueue        InputQueue           ExecutorService        RenderQueue

Timer: lavender-timer (ScheduledExecutorService)
  ├── DeltaBuffer 50ms 批量刷新
  └── ResponseTimer 每秒推送
```

### 1.2 新增组件

| 组件 | 职责 | 所在线程 |
|------|------|---------|
| `ProviderSelector` | 方向键列表选择界面 | 主线程（阻塞至选定） |
| `MarkdownRenderer` | flexmark-java 封装，纯文本→`List<RenderedLine>` | render 线程 |
| `ResponseTimer` | 纳秒级计时，暴露 `elapsedSeconds()` | network 线程启动 / timer 线程读取 |

### 1.3 改动文件清单

| 类型 | 文件 | 改动摘要 |
|------|------|---------|
| **修改** | `core/config/ProviderConfig.java` | 加 `name`；`baseUrl` 改可为 null；搬入 `thinking` |
| **修改** | `core/config/ThinkingConfig.java` | 无变化（从 Options 移至 ProviderConfig） |
| **修改** | `core/config/Options.java` | 移除 `thinking` 字段 |
| **修改** | `core/config/LlmConfig.java` | `provider`→`providers: List<ProviderConfig>` |
| **修改** | `core/config/ConfigLoader.java` | 适配列表校验，错误带索引 |
| **修改** | `LavenderCode.java` | 单/多 provider 分支 → 选择器 |
| **修改** | `chat/terminal/TerminalChatApplication.java` | 接收 `ProviderConfig`，传 name 给各组件 |
| **修改** | `chat/terminal/TerminalRenderer.java` | 状态栏三列；FinalizeMessage 触发 markdown 渲染 |
| **修改** | `chat/terminal/NetworkOrchestrator.java` | 丢弃 Thinking delta；集成 ResponseTimer |
| **修改** | `chat/terminal/InputSystem.java` | Ctrl+C → EXIT |
| **修改** | `chat/terminal/TerminalKeyReader.java` | Alt+Enter 识别 |
| **修改** | `chat/terminal/LavenderSplash.java` | 追加版本/CWD |
| **修改** | `chat/terminal/DeltaBuffer.java` | 移除 THINK_DELTA 类型 |
| **修改** | `chat/terminal/DeltaEvent.java` | 移除 Thinking 变体 |
| **修改** | `chat/terminal/RenderEvent.java` | 移除 ThinkDelta；扩展 StatusUpdate |
| **修改** | `chat/terminal/StreamingChatService.java` | ThinkingDelta → null 跳过 |
| **修改** | `chat/terminal/MessageBlock.java` | 移除 thinking segment 渲染，加 `getRawText()` |
| **修改** | `core/openai/OpenAIProvider.java` | thinking 静默忽略 |
| **修改** | `core/anthropic/AnthropicProvider.java` | thinking 从 ProviderConfig 取 |
| **新增** | `chat/terminal/ProviderSelector.java` | 方向键选择界面 |
| **新增** | `chat/terminal/MarkdownRenderer.java` | flexmark 封装 |
| **新增** | `chat/terminal/ResponseTimer.java` | 纳秒计时，每秒回调 |

---

## 二、模块一：配置层改造

### 2.1 ProviderConfig 字段变更

```java
public record ProviderConfig(
    @JsonProperty("name")
    String name,          // 新增：可读名称，可为 null（默认用 "protocol-model"）

    @JsonProperty("protocol")
    @NotNull
    String protocol,

    @JsonProperty("model")
    @NotNull
    String model,

    @JsonProperty("base_url")
    String baseUrl,       // 改：可为 null（null → 用协议默认端点）

    @JsonProperty("api_key")
    @NotNull
    String apiKey,

    @JsonProperty("thinking")
    ThinkingConfig thinking  // 改：从 Options 搬入
) {
    public ProviderConfig {
        if (thinking == null) thinking = new ThinkingConfig();
    }
}
```

### 2.2 LlmConfig 结构变更

```java
public record LlmConfig(
    @JsonProperty("providers")
    @NotNull
    @Valid
    List<ProviderConfig> providers,  // 改：单 provider → 列表

    @JsonProperty("options")
    Options options
) {
    public LlmConfig {
        if (options == null) options = new Options();
        // options 不再含 thinking
    }
}
```

### 2.3 Options 变更

移除 `thinking` 字段。`Options` 仅保留 `maxTokens` 和 `systemPrompt`。

### 2.4 config.yaml 新版示例

```yaml
providers:
  - name: DeepSeek
    protocol: openai
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: sk-xxx
    thinking:
      enabled: true
      budget_tokens: 4000

options:
  max_tokens: 4096
  system_prompt: "You are a helpful AI assistant."
```

### 2.5 ConfigLoader 校验增强

- `providers` 列表为空的错误：`"配置错误: providers 不能为空"`
- 逐项校验依赖 Jakarta `@NotNull`，`ConstraintViolationException` → 输出 `"配置错误: providers[{i}].{字段} 缺失"`
- YAML 格式错误：捕获 `JsonParseException` → 输出 `"YAML 格式错误: {message}"`（不输出堆栈）
- 均 `System.exit(1)`

---

## 三、模块二：Provider 选择器

### 3.1 组件设计

新增 `ProviderSelector`，全部为静态方法，不持有状态。

```
public static ProviderConfig select(Terminal terminal, List<ProviderConfig> providers)
    throws InterruptedException;
```

### 3.2 界面布局

```
LavenderCode v2.0
cwd: /home/user/project

请选择 AI Provider:

  ● DeepSeek (deepseek-chat)
    Claude (claude-sonnet-4-20250514)
    Qwen (qwen-max)

↑↓ 选择  Enter 确认  Ctrl+C 退出
```

### 3.3 交互实现

1. 创建临时 `TerminalKeyReader`，不进入完整 InputSystem 管线
2. `TerminalKeyReader.readInput()` 读取方向键 `↑` / `↓` → 移动 `selectedIndex`
3. Enter → 返回 `providers.get(selectedIndex)`
4. Ctrl+C → `System.exit(0)`（先恢复终端设置）
5. 每按一次方向键重绘列表（`terminal.puts(cursor_address, ...)` 定位更新，非全屏清除）

### 3.4 调用点

`LavenderCode.main()` 中：

```java
ProviderConfig selectedProvider;
if (config.providers().size() == 1) {
    selectedProvider = config.providers().get(0);
} else {
    selectedProvider = ProviderSelector.select(terminal, config.providers());
}
```

### 3.5 默认名称策略

`ProviderConfig.name` 为 null 时，显示名称取 `protocol + "-" + model`（如 `"openai-deepseek-chat"`）。

---

## 四、模块三：流式协议层

### 4.1 Thinking 支持（Anthropic）

`AnthropicProvider.buildRequestBody()` 中 thinking 配置来源改为 `config.provider().thinking()`（从 `LlmConfig` 传入单个 `ProviderConfig`，而非整个 `LlmConfig`）。

当前 `streamChat(List<Message>, LlmConfig)` 签名不变，内部从 `config.provider()` 取 thinking 配置。

### 4.2 Thinking 支持（OpenAI）

`OpenAIProvider.buildRequestBody()` 中检测 `thinking.enabled == true` 时静默忽略，不向请求体注入 thinking 相关字段，也不抛异常。

### 4.3 Thinking Delta 丢弃链路

**修改前链路**：
```
StreamEvent.ThinkingDelta → DeltaEvent.Thinking → BufferedEvent(THINK_DELTA) → ThinkDelta → 斜体渲染
```

**修改后链路**：
```
StreamEvent.ThinkingDelta → null（跳过，不产生 DeltaEvent）
```

**具体改动**：

1. `StreamingChatService.toDeltaEvent()`：
```java
case StreamEvent.ThinkingDelta -> null;  // 丢弃
```

2. `StreamingChatService.submit()` 中迭代逻辑：
```java
DeltaEvent delta = toDeltaEvent(event);
if (delta != null) onDelta.accept(ctx, delta);
```

3. 删除 `DeltaEvent.Thinking` 记录
4. 删除 `DeltaBuffer.BufferedEvent.Type.THINK_DELTA`
5. 删除 `RenderEvent.ThinkDelta`
6. `TerminalRenderer.dispatch()` 移除 `ThinkDelta` 分支
7. `MessageBlock` 中 `ThinkingSegment` 和 `appendThinking()` 方法保留但不再有调用路径（避免连锁改动）

### 4.4 DeltaEvent 收敛后

```java
public sealed interface DeltaEvent permits
    DeltaEvent.Content,
    DeltaEvent.Complete,
    DeltaEvent.Error,
    DeltaEvent.Usage {}
```

---

## 五、模块四：Markdown 重渲染

### 5.1 组件设计

新增 `MarkdownRenderer`：

```java
public final class MarkdownRenderer {
    private static final DataHolder OPTIONS = new MutableDataSet()
        .set(Parser.EXTENSIONS, Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create()
        ));

    /**
     * 将 markdown 文本解析为带样式的 RenderedLine 列表。
     * @param markdown 原始 markdown 文本
     * @param width 终端显示宽度（用于换行）
     * @return 样式化的行列表
     */
    public static List<RenderedLine> render(String markdown, int width);
}
```

### 5.2 样式映射

| Markdown AST 节点 | JLine AttributedStyle |
|-------------------|----------------------|
| Heading (1–6) | bold + foreground 品红 (205) |
| StrongEmphasis (`**text**`) | bold |
| Emphasis (`*text*`) | italic, foreground cyan (同现有 THINKING_TEXT 色) |
| Code (inline) | background 灰色 (236) |
| FencedCodeBlock | 同现有 CODE_BLOCK：background 灰色(236)，语言标签 foreground 灰色(136) |
| BulletList / OrderedList | 缩进 2 格 + `• ` / `N. ` 前缀 |
| BlockQuote (`> text`) | 前缀 `│ `，foreground 暗色(244) |
| Strikethrough (`~~text~~`) | strikethrough |
| Paragraph / SoftLineBreak | 默认白色 |

### 5.3 调用时机

`TerminalRenderer.dispatch()` 处理 `FinalizeMessage` 时：

```java
case RenderEvent.FinalizeMessage() -> {
    if (currentAIBlock != null) {
        String rawText = currentAIBlock.getRawText();          // 拼接所有 ContentSegment.rawText
        List<RenderedLine> styled = MarkdownRenderer.render(rawText, terminalWidth);
        currentAIBlock.replaceLines(styled);                   // 替换渲染缓存
        currentAIBlock.markComplete();
        flatCacheDirty = true;
        drawFull();                                            // 全量重绘
        currentAIBlock = null;
    }
}
```

`MessageBlock` 新增 `getRawText()` 拼接所有 `ContentSegment.rawText`；新增 `replaceLines(List<RenderedLine>)` 直接覆盖渲染缓存（绕过 wrapAndColor 的流式管线）。

---

## 六、模块五：状态栏 + 响应计时

### 6.1 状态栏三列布局

```
DeepSeek              │  Imagining… (5s)  │     deepseek-chat
```

| 列 | 内容 | 对齐 | 来源 |
|-----|------|------|------|
| 左 | Provider 可读名称 | 左对齐，占 1/3 宽度 | `ProviderConfig.name` |
| 中 | 状态文本 | 居中，占 1/3 宽度 | 计时器/空闲 |
| 右 | 模型名 | 右对齐，占 1/3 宽度 | `ProviderConfig.model` |

分隔符 `│` 位置固定位于列交界处，列宽 = 终端列数 / 3。

### 6.2 RenderEvent 扩展

`StatusUpdate` 从当前：
```java
record StatusUpdate(String model, int tokenCount, boolean isEstimating) {}
```
扩展为：
```java
record StatusUpdate(
    String providerName,
    String modelName,
    String statusText,    // null=空闲, "Imagining… (Ns)", "Done (Ns)"
    int tokenCount
) {}
```

### 6.3 ResponseTimer

```java
public final class ResponseTimer {
    private volatile long startNanos;
    private volatile long stopNanos;

    public void start() { this.startNanos = System.nanoTime(); }
    public long elapsedSeconds() {
        long end = stopNanos > 0 ? stopNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000_000;
    }
    public void stop() { this.stopNanos = System.nanoTime(); }
}
```

### 6.4 计时流程

```
NetworkOrchestrator.handleSendMessage()
  │
  ├── 1. 创建 ResponseTimer timer = new ResponseTimer(); timer.start()
  ├── 2. 提交请求到 StreamingChatService
  ├── 3. 启动每秒定时任务（scheduleAtFixedRate, 1s interval）:
  │        safePut(new StatusUpdate(name, model, "Imagining… (" + timer.elapsedSeconds() + "s)", tokens))
  │
  ├── 4. 收到 DeltaEvent.Complete / .Error:
  │        timer.stop()
  │        cancelScheduledTask()
  │        safePut(new StatusUpdate(name, model, "Done (" + timer.elapsedSeconds() + "s)", tokens))
  │
  └── 5. 标记 FinalizeMessage 后 1 秒:
           safePut(new StatusUpdate(name, model, null, tokens))  // 清空中间列
```

定时任务通过 `ScheduledExecutorService timerScheduler`（复用已有的 `lavender-timer`）执行，与 DeltaBuffer 共享同一调度器，互不冲突。

---

## 七、模块六：启动横幅修订

### 7.1 LavenderSplash 追加内容

现有 splash 显示像素薰衣草图案 + 标语文案后，追加三行：

```java
// 追加应用信息
terminal.writer().println();
terminal.writer().println(center("LavenderCode v" + VERSION, termWidth));
terminal.writer().println(center("cwd: " + System.getProperty("user.dir"), termWidth));
terminal.writer().println("─".repeat(termWidth));
terminal.flush();
```

### 7.2 版本号来源

在 `LavenderSplash` 中定义常量：`private static final String VERSION = "2.0.0";`

### 7.3 工作目录

`System.getProperty("user.dir")`，截断策略：若路径长度超过终端宽度，截去前段，显示 `"…" + 末尾 N 字符`。

---

## 八、模块七：输入系统修订

### 8.1 Alt+Enter 识别

`TerminalKeyReader.readInput()` 中，ESC (0x1B) 后的序列读取：

```java
private TerminalInput readEscapeSequence() {
    int next = timedRead();
    if (next < 0) return new TerminalInput.Character(0x1B);
    if (next == '\n' || next == '\r') {
        // Alt+Enter → 换行
        if (next == '\r') consumeLfIfPresent();
        return new TerminalInput.NewlineWithoutSubmit();
    }
    // ... 现有 CSI/SS3/mouse 分支
}
```

新增 `TerminalInput.NewlineWithoutSubmit` 变体，区别于 `Submit()`，表示插入换行而不提交。

### 8.2 Ctrl+C 退出

`InputSystem.readEditedLine()` 中，当前 Ctrl+C (0x03) 的处理：

```java
// 旧
} else if (c == 3) {  // Ctrl+C
    publishDraftSync("", 0);
    return TerminalInput.Submit();  // ← 改为
    return TerminalInput.CancelOrExit();  // 触发 EXIT
}
```

`InputSystem.parseCommand()` 中新增 `CancelOrExit` 映射：

```java
if (cmd.type == CommandType.CANCEL) {  // 保留 /cancel 命令
    return new InputEvent.ExecuteCommand(CommandType.CANCEL, "");
}
```

`InputSystem.readEditedLine()` 的最终返回处，若收到 `TerminalInput.CancelOrExit`，发送 `ExecuteCommand(EXIT)`。

### 8.3 Bracketed Paste 清理

`InputSystem` 退出时调用 `TerminalKeyReader.disableBracketedPaste()`。

---

## 九、模块八：跨协议一致体验

### 9.1 错误消息格式统一

**Anthropic** 当前 error 消息：
```java
"Anthropic API error (HTTP " + response.code() + "): " + errorBody
```

**OpenAI** 当前 error 消息：
```java
"OpenAI API error (HTTP " + response.code() + "): " + errorBody
```

统一为格式：`"{protocol} API error (HTTP {code}): {body}"`。

### 9.2 NetworkOrchestrator 传入 providerName

`NetworkOrchestrator` 当前持有 `modelName` 字段。新增 `providerName` 字段，用于渲染错误信息时区分协议来源。

---

## 十、模块九：非功能验收

### 10.1 N4 配置健壮性

已在 2.5 节覆盖。补充：`LavenderCode.main()` 的 catch 块对 `ConfigException`（自定义异常）输出友好信息，其余 `Exception` 仍输出错误摘要（不输出完整堆栈）。

### 10.2 N5 密钥安全

无代码改动。当前 `apiKey` 仅在 Provider 层用于构造 HTTP 请求头（`Authorization: Bearer` / `x-api-key`），不经 RenderQueue、输入队列或日志输出。验收时人工验证即可。

### 10.3 N6 终端兼容与自适应

已有 `WINCH` 处理器 + `MessageBlock.reflow()`。新增验证：markdown 渲染完成后调用 `reflow()` 确保列宽自适应。

### 10.4 N7 退出整洁

`TerminalRenderer.run()` finally 块：
```java
finally {
    terminal.puts(InfoCmp.Capability.cursor_visible);
    terminal.puts(InfoCmp.Capability.exit_ca_mode);
    terminal.flush();
}
```

`InputSystem` 退出时调用 `TerminalKeyReader.disableBracketedPaste()`（已有 enable，退出时补充 disable）。

---

## 十一、数据流总览

```
程序启动
  │
  ├── ConfigLoader.load("config.yaml")
  │     ├── 解析 providers 列表
  │     ├── 逐项 Jakarta 校验
  │     └── 失败 → 清晰错误 + exit(1)
  │
  ├── providers.size() == 1?
  │     ├── YES → 直接采用
  │     └── NO  → ProviderSelector.select(terminal, providers)
  │
  ├── ProviderRegistry.get(protocol) → LlmProvider
  ├── 创建 InMemorySessionManager
  ├── 创建 TerminalChatApplication(providerConfig包含name, LlmProvider, ...)
  │
  └── TerminalChatApplication.run()
        │
        ├── LavenderSplash.show() → 更新版（含版本/CWD）
        ├── drawFull()
        │
        └── 事件循环
              │
              ├── [用户输入] InputSystem → InputQueue → NetworkOrchestrator
              │     ├── 普通消息 → StreamingChatService.submit()
              │     │     ├── timer.start() + 每秒 StatusUpdate
              │     │     ├── provider.streamChat()
              │     │     │     ├── Content → ContentDelta → 逐字渲染
              │     │     │     ├── Thinking → null（丢弃）
              │     │     │     ├── Complete → timer.stop() → FinalizeMessage
              │     │     │     └── Error → 错误渲染
              │     │     └── onDelta → DeltaBuffer(50ms batch) → RenderQueue
              │     └── 命令 → /exit, /clear, /help, /cancel, /scroll
              │
              ├── [渲染] TerminalRenderer
              │     ├── AppendToMessage → 逐字追加到 MessageBlock
              │     ├── FinalizeMessage → MarkdownRenderer.render() → 替换 → drawFull()
              │     ├── StatusUpdate → 三列状态栏（含计时）
              │     └── Shutdown → 恢复终端 → exit
              │
              └── TerminalKeyReader
                    ├── Alt+Enter → NewlineWithoutSubmit
                    ├── Ctrl+C → EXIT
                    └── Enter → Submit
```

---

## 十二、测试策略

| 层次 | 测试内容 |
|------|----------|
| 单元测试 | `ProviderConfig` 序列化/反序列化；`ConfigLoader` 异常场景；`ResponseTimer.elapsedSeconds()`；`MarkdownRenderer.render()` 各节点样式；`TerminalKeyReader` Alt+Enter 序列 |
| 集成测试 | `TerminalChatIntegrationTest` 扩展：多 provider 选择、thinking 丢弃、状态栏格式、Ctrl+C 退出 |
| 契约测试 | `AnthropicProviderContractTest` / `OpenAIProviderContractTest`：新增 thinking=true 场景的请求体验证 |
| 手动验收 | N5 密钥安全审查、N6 终端兼容性测试（Windows Terminal / iTerm2 / GNOME Terminal / Alacritty）、N7 退出整洁验证 |
