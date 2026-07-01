# LavenderCode v1.0 对话系统设计文档

| Key          | Value                                                                                             |
| ------------ | ------------------------------------------------------------------------------------------------- |
| **Version**  | v1.0                                                                                              |
| **Status**   | 已确认                                                                                            |
| **Date**     | 2026-06-15                                                                                        |
| **PRD**      | [LavenderCode v1 PRD](../../current/modules/LavenderCode/2026-06-01-lavendercode-v1-prd.md) |

---

## 目录

1. [设计目标](#一设计目标)
2. [架构设计](#二架构设计)
3. [核心设计](#三核心设计)
4. [测试策略](#四测试策略)
5. [数据流](#五数据流)
6. [工程决策记录](#六工程决策记录)

---

## 一、设计目标

Phase 1 交付一个**纯对话系统**，基于 [PRD](../../current/modules/LavenderCode/2026-06-01-lavendercode-v1-prd.md) 中定义的 MVP 范围：

- **纯聊模式** -- 仅支持连续对话，无 Agent 编排、无 Tool-use、无多模态。
- **双 Provider** -- Anthropic (Claude) + OpenAI (GPT) 作为首批 LLM 后端。
- **TDD 分层测试** -- 三层测试体系（单元测试 + 契约测试 + 集成测试）保证质量。
- **抽象 Provider 层** -- `LlmProvider` 接口隔离业务逻辑与 LLM 协议细节。
- **SSE 流式输出** -- 所有 Provider 统一以 Server-Sent Events 方式流式返回 Token，TUI 逐段渲染。
- **本地会话管理** -- 对话历史保存在内存中，支持基本的上下文维持。

---

## 二、架构设计

### 2.1 整体包结构

采用 **mixed-layer 包结构**，核心层与业务层混合组织，避免过度抽象：

```
com.lavendercode
  ├── chat/
  │   ├── tui/                  # Terminal UI (Lanterna)
  │   │   ├── App.java          # 应用入口
  │   │   ├── Layout.java       # 三栏布局管理器
  │   │   ├── ChatPanel.java    # 聊天内容面板（滚动）
  │   │   ├── InputPanel.java   # 底部输入框
  │   │   └── StatusBar.java    # 顶部状态栏
  │   │
  │   └── session/             # 会话管理
  │       ├── SessionManager.java        # 接口
  │       └── InMemorySessionManager.java # 内存实现
  │
  ├── core/
  │   ├── config/              # 配置加载
  │   │   ├── LlmConfig.java
  │   │   ├── ProviderConfig.java
  │   │   ├── Options.java
  │   │   ├── ThinkingConfig.java
  │   │   └── ConfigLoader.java
  │   │
  │   ├── provider/            # LLM Provider 抽象
  │   │   ├── LlmProvider.java         # 核心接口
  │   │   └── LlmProviderFactory.java  # 工厂类
  │   │
  │   ├── sse/                 # SSE 解析器
  │   │   └── SseParser.java
  │   │
  │   ├── model/               # 数据模型
  │   │   ├── Message.java
  │   │   ├── Role.java
  │   │   ├── StreamEvent.java
  │   │   └── StreamEventIterator.java
  │   │
  │   ├── anthropic/           # Anthropic Provider 实现
  │   │   └── AnthropicProvider.java
  │   │
  │   └── openai/              # OpenAI Provider 实现
  │       └── OpenAIProvider.java
```

### 2.2 架构决策总表

| 决策 | 选择 | 理由 |
|------|------|------|
| 构建工具 | Maven | 团队最熟悉，生态成熟，Surefire + Failsafe 原生支持 TDD |
| TUI 框架 | Lanterna | 纯 Java 终端库，无 JNI，跨平台一致，支持 256 色 |
| HTTP 客户端 | OkHttp 4.x | 连接池、Interceptor、超时控制优于 java.net.HttpClient |
| 序列化 | Jackson YAML | YAML 对人友好，Jackson 注解驱动，易于校验 |
| 测试框架 | JUnit 5 + Mockito + MockWebServer | 原生 TDD 支持；MockWebServer 模拟 HTTP 端点 |
| JDK | JDK 17 | LTS，sealed class/interface、record、pattern matching |
| 流式模型 | 自定义 `Iterator<StreamEvent>` | 统一消费端，简化 TUI 渲染逻辑 |

### 2.3 分层依赖图

```
┌─────────────────────────────────────────────────────┐
│                   TUI (Lanterna)                      │
│     Layout / ChatPanel / InputPanel / StatusBar      │
└────────────────────┬────────────────────────────────┘
                     │ SessionManager
┌────────────────────▼────────────────────────────────┐
│              Session Management                       │
│          InMemorySessionManager                       │
└────────────────────┬────────────────────────────────┘
                     │ List<Message>
┌────────────────────▼────────────────────────────────┐
│              LlmProvider (interface)                  │
│              LlmProviderFactory                       │
├──────────────┬──────────────────┬────────────────────┤
│              │                  │                    │
│  ┌───────────▼────────┐ ┌──────▼───────────┐        │
│  │ AnthropicProvider  │ │ OpenAIProvider   │        │
│  │ /v1/messages       │ │ /v1/chat/        │        │
│  │                    │ │ completions      │        │
│  └───────────┬────────┘ └──────┬───────────┘        │
│              │                 │                     │
│  ┌───────────▼─────────────────▼───────────┐        │
│  │            SseParser                      │        │
│  │ (InputStream -> StreamEventIterator)      │        │
│  └───────────────────────────────────────────┘        │
│                    │                                  │
│  ┌─────────────────▼─────────────────────────┐        │
│  │           OkHttp 4.x Client                │        │
│  └───────────────────────────────────────────┘        │
└───────────────────────────────────────────────────────┘
```

---

## 三、核心设计

### 3.1 Provider 接口

```java
package com.lavendercore.core.provider;

import com.lavendercore.core.model.Message;
import com.lavendercore.core.model.StreamEventIterator;
import com.lavendercore.core.config.LlmConfig;

import java.util.List;

/**
 * LLM Provider 抽象接口 —— 所有后端 LLM 均通过此接口统一暴露。
 */
public interface LlmProvider extends AutoCloseable {

    /** 返回协议标识，如 "anthropic"、"openai" */
    String protocol();

    /**
     * 流式聊天 —— 发送消息列表并返回事件流迭代器。
     *
     * @param messages  对话历史 + 新消息
     * @param config    模型、参数、Thinking 配置
     * @return          可遍历的 StreamEvent 流
     */
    StreamEventIterator streamChat(List<Message> messages, LlmConfig config);
}
```

### 3.2 数据模型

#### Message / Role

```java
package com.lavendercore.core.model;

/**
 * 单条对话消息。
 *
 * @param role    消息角色
 * @param content 文本内容（Markdown 格式）
 */
public record Message(Role role, String content) {}

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT
}
```

#### StreamEvent (sealed interface)

```java
package com.lavendercore.core.model;

/**
 * 流式事件 —— sealed interface 确保事件种类可穷举。
 */
public sealed interface StreamEvent
        permits ContentDelta, ThinkingDelta, StreamComplete, StreamError {

    /** 普通内容增量 */
    record ContentDelta(String delta) implements StreamEvent {}

    /** Thinking 块增量（Anthropic 特有） */
    record ThinkingDelta(String delta) implements StreamEvent {}

    /** 流结束 */
    record StreamComplete(String stopReason) implements StreamEvent {}

    /** 流错误 */
    record StreamError(String errorCode, String message) implements StreamEvent {}
}
```

#### StreamEventIterator

```java
package com.lavendercore.core.model;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 流事件迭代器 —— 在 SSE InputStream 之上封装 Iterator 模式，
 * 使消费方（TUI）可以用统一的 for-each 语义消费流。
 */
public interface StreamEventIterator extends Iterator<StreamEvent>, AutoCloseable {

    @Override
    boolean hasNext();

    @Override
    StreamEvent next() throws NoSuchElementException;

    @Override
    void close();
}
```

### 3.3 AnthropicProvider

```java
package com.lavendercore.core.anthropic;

/**
 * Anthropic Claude Provider 实现。
 *
 * HTTP 细节：
 *   - Endpoint: POST https://api.anthropic.com/v1/messages
 *   - 请求头：
 *       x-api-key:           {apiKey}
 *       anthropic-version:   2023-06-01
 *       Content-Type:        application/json
 *   - 请求体：
 *       {
 *         "model":      "claude-sonnet-4-20250514",
 *         "max_tokens": 8192,
 *         "thinking":   { "type": "enabled", "budget_tokens": 4096 },
 *         "messages":   [
 *           { "role": "user", "content": "Hello" }
 *         ]
 *       }
 *
 * SSE 事件流：
 *   - event: content_block_start
 *   - event: content_block_delta
 *       data: {"type":"content_block_delta","delta": {"type":"text_delta","text":"Hello"}}
 *   - event: content_block_delta  (thinking)
 *       data: {"type":"content_block_delta","delta": {"type":"thinking_delta","thinking":"..."}}
 *   - event: message_delta
 *   - event: message_stop
 *
 * 映射规则：
 *   text_delta      -> ContentDelta
 *   thinking_delta  -> ThinkingDelta
 *   message_stop    -> StreamComplete
 *    HTTP/IO 异常   -> StreamError
 */
public class AnthropicProvider implements LlmProvider {

    private final OkHttpClient httpClient;
    private final String apiKey;

    public AnthropicProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)  // SSE 需无限读超时
                .build();
    }

    @Override
    public String protocol() { return "anthropic"; }

    @Override
    public StreamEventIterator streamChat(List<Message> messages, LlmConfig config) {
        // 1. 构建 JSON 请求体（含 thinking blocks）
        // 2. 创建 OkHttp POST Request（含 x-api-key 头）
        // 3. 执行异步 / 同步调用
        // 4. 返回 SseParser.parse(response.body().byteStream()) 包装的迭代器
    }

    @Override
    public void close() {
        // OkHttpClient dispatcher/connectionPool 清理
    }
}
```

### 3.4 OpenAIProvider

```java
package com.lavendercore.core.openai;

/**
 * OpenAI GPT Provider 实现。
 *
 * HTTP 细节：
 *   - Endpoint: POST https://api.openai.com/v1/chat/completions
 *   - 请求头：
 *       Authorization: Bearer {apiKey}
 *       Content-Type:  application/json
 *   - 请求体：
 *       {
 *         "model":       "gpt-4o",
 *         "max_tokens":  4096,
 *         "stream":      true,
 *         "messages":    [
 *           { "role": "system", "content": "You are a helpful assistant." },
 *           { "role": "user",   "content": "Hello" }
 *         ]
 *       }
 *
 * SSE 事件流：
 *   data: {"id":"...","object":"chat.completion.chunk",
 *          "choices":[{"delta":{"content":"Hello"},"index":0}]}
 *   data: {"id":"...","object":"chat.completion.chunk",
 *          "choices":[{"delta":{},"finish_reason":"stop","index":0}]}
 *   data: [DONE]
 *
 * 映射规则：
 *   choices[0].delta.content 非 null                -> ContentDelta
 *   choices[0].finish_reason 非 null                 -> StreamComplete
 *   [DONE] 行                                        -> 终止迭代
 *   HTTP/IO 异常 / 非 [DONE] 的意外格式              -> StreamError
 *
 * 注意：OpenAI 没有 thinking 流，因此永不产生 ThinkingDelta。
 */
public class OpenAIProvider implements LlmProvider {

    private final OkHttpClient httpClient;
    private final String apiKey;

    public OpenAIProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String protocol() { return "openai"; }

    @Override
    public StreamEventIterator streamChat(List<Message> messages, LlmConfig config) {
        // 1. system prompt 从 messages 中分离并放入 system 参数
        // 2. 构建 ChatCompletionRequest JSON
        // 3. 创建 OkHttp POST Request（含 Bearer 头）
        // 4. 返回 SseParser.parse(response.body().byteStream()) 包装的迭代器
    }

    @Override
    public void close() {
        // OkHttpClient 清理
    }
}
```

### 3.5 SSE 解析器

```java
package com.lavendercore.core.sse;

/**
 * Server-Sent Events 解析器 —— 共享工具类。
 *
 * 所有 Provider 共用此解析器，将 InputStream 中的 SSE 协议数据
 * 转换为统一的 StreamEvent 流。
 *
 * SSE 协议格式（RFC 8895 子集）：
 *
 *   [whitespace]
 *   :comment
 *   event: {eventType}
 *   id:    {eventId}
 *   retry: {ms}
 *   data:  {single line payload}
 *   data:  {continued line payload}    <- 多行 data 拼接
 *                                    <- 空行 = 事件分隔符
 *
 * 解析策略：
 *   - 忽略注释行（":" 开头）
 *   - 忽略 event / id / retry 行（当前不需要）
 *   - data: 后面的内容收集到 StringBuilder
 *   - 空行触发一次事件回调
 *   - 每个 Provider 自行实现 DataCallback 将 JSON 映射为 StreamEvent
 *
 * 线程安全：否（InputStream 本身非线程安全）
 */
public class SseParser {

    @FunctionalInterface
    public interface DataCallback {
        StreamEvent onData(String data) throws IOException;
    }

    /**
     * 从 InputStream 中读取 SSE 事件，逐行解析，每次遇到空行
     * 调用 callback 生成一个 StreamEvent，通过 Iterator 惰性返回。
     */
    public static StreamEventIterator parse(
            InputStream inputStream,
            DataCallback callback
    ) {
        // 1. 用 BufferedReader 包装 InputStream (UTF-8)
        // 2. 逐行读取：
        //    - 行首为 ":" -> 跳过（注释）
        //    - 行首为 "data:" -> 提取 ":" 后的内容，追加到 buffer
        //    - 空行 -> 调用 callback.onData(buffer.toString()) 产生事件
        //    - 其他前缀 -> 跳过
        // 3. 返回 StreamEventIterator 实现（hasNext 阻塞读取下一事件）
    }
}
```

### 3.6 配置模型

```java
package com.lavendercore.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

/**
 * LLM 配置顶层模型。
 */
@JsonDeserialize
public record LlmConfig(

    @NotNull
    @JsonProperty("provider")
    ProviderConfig provider,

    @NotNull
    @JsonProperty("options")
    Options options,

    @JsonProperty("thinking")
    ThinkingConfig thinking

) {}

/**
 * Provider 连接配置。
 */
public record ProviderConfig(

    @NotBlank
    @JsonProperty("type")         // "anthropic" | "openai"
    String type,

    @NotBlank
    @JsonProperty("api_key")
    String apiKey,

    @JsonProperty("base_url")
    String baseUrl                // 默认 https://api.anthropic.com

) {}

/**
 * 模型与生成参数。
 */
public record Options(

    @NotBlank
    @JsonProperty("model")
    String model,                 // "claude-sonnet-4-20250514" | "gpt-4o"

    @Min(1) @Max(65536)
    @JsonProperty("max_tokens")
    int maxTokens,                // 默认 4096

    @Min(0) @Max(2)
    @JsonProperty("temperature")
    Double temperature            // 可选

) {}

/**
 * Thinking 块配置（Anthropic 专用）。
 */
public record ThinkingConfig(

    @JsonProperty("enabled")
    boolean enabled,              // 默认 false

    @Min(1) @Max(65536)
    @JsonProperty("budget_tokens")
    int budgetTokens              // 默认 2048

) {}
```

#### ConfigLoader

```java
package com.lavendercore.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.io.IOException;
import java.nio.file.Path;

/**
 * YAML 配置文件加载器。
 *
 * 1. 用 Jackson YAMLFactory 反序列化 YAML -> LlmConfig
 * 2. 用 Jakarta Validation API 校验约束
 * 3. 校验失败抛 ConfigValidationException
 *
 * 配置文件示例 (lavendercode.yaml)：
 *
 * ```yaml
 * provider:
 *   type:    anthropic
 *   api_key: ${ANTHROPIC_API_KEY}
 *   base_url: https://api.anthropic.com
 * options:
 *   model:      claude-sonnet-4-20250514
 *   max_tokens: 8192
 *   temperature: 0.7
 * thinking:
 *   enabled:      true
 *   budget_tokens: 4096
 * ```
 */
public class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory()
            .getValidator();

    public static LlmConfig load(Path yamlPath) throws IOException {
        LlmConfig config = MAPPER.readValue(yamlPath.toFile(), LlmConfig.class);
        var violations = VALIDATOR.validate(config);
        if (!violations.isEmpty()) {
            throw new ConfigValidationException(violations);
        }
        return config;
    }
}
```

### 3.7 会话管理

```java
package com.lavendercore.chat.session;

import com.lavendercore.core.model.Message;
import java.util.List;

/**
 * 会话管理器 —— 负责维护单轮对话的消息历史。
 */
public interface SessionManager {

    /** 添加用户消息并返回更新后的不可变消息列表 */
    List<Message> addUserMessage(String content);

    /** 添加助手消息并返回更新后的不可变消息列表 */
    List<Message> addAssistantMessage(String content);

    /** 获取当前不可变历史快照 */
    List<Message> getHistory();

    /** 清空当前会话 */
    void clear();

    /** 当前消息总数 */
    int size();
}
```

```java
package com.lavendercore.chat.session;

import com.lavendercore.core.model.Message;
import com.lavendercore.core.model.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存会话管理器 —— 全部历史保存在 ArrayList 中。
 *
 * 对外暴露的 List 均为 Collections.unmodifiableList 包装的
 * 防御性副本，防止外部修改。
 */
public class InMemorySessionManager implements SessionManager {

    private final List<Message> messages = new ArrayList<>();
    private final String systemPrompt;

    public InMemorySessionManager(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message(Role.SYSTEM, systemPrompt));
        }
    }

    @Override
    public List<Message> addUserMessage(String content) {
        messages.add(new Message(Role.USER, content));
        return defensiveCopy();
    }

    @Override
    public List<Message> addAssistantMessage(String content) {
        messages.add(new Message(Role.ASSISTANT, content));
        return defensiveCopy();
    }

    @Override
    public List<Message> getHistory() {
        return defensiveCopy();
    }

    @Override
    public void clear() {
        messages.clear();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message(Role.SYSTEM, systemPrompt));
        }
    }

    @Override
    public int size() {
        return messages.size();
    }

    private List<Message> defensiveCopy() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
}
```

### 3.8 TUI 设计

**三栏布局示意图：**

```
┌──────────────────────────────────────────────────────────┐
│ [Anthropic] claude-sonnet-4-20250514 | Tokens: 1,234     │ ← StatusBar
├──────────────────────────────────────────────────────────┤
│                                                          │
│  User: 实现一个 Java 终端聊天应用                          │
│                                                          │
│  Assistant: 我来设计一个分层架构...                        │
│  ┌─ Thinking ──────────────────────────────────────────┐ │
│  │ 用户需要的是一个终端聊天应用，结合前文...              │ │
│  │ 第一步，确定核心接口...                               │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                          │
│  最终输出 Markdown 格式的代码...                          │
│                                                          │
│  ▊                                                        │ ← Streaming cursor
│                                                          │
├──────────────────────────────────────────────────────────┤
│ > /help                                                   │ ← InputPanel
└──────────────────────────────────────────────────────────┘
```

**Layout 组件职责：**

| 组件 | 职责 | 关键 API |
|------|------|----------|
| `StatusBar` | 顶部状态：当前 Provider、模型名、Token 计数 | `updateStatus(provider, model, tokens)` |
| `ChatPanel` | 聊天内容滚动区域，实时追加流式文本 | `appendContent(delta)`, `appendThinking(delta)`, `scrollToBottom()` |
| `InputPanel` | 底部输入行，支持多行编辑、命令前缀 | `readLine() -> String`, 监听 `/exit\|/clear\|/help` |
| `Layout` | 三栏组合，窗口 resize 自适应 | `init(screen)`, `refresh()`, `close()` |

**App 主循环伪代码：**

```
1. 初始化 Lanterna Terminal
2. 初始化 Layout（StatusBar + ChatPanel + InputPanel）
3. 加载配置 -> ConfigLoader.load("lavendercode.yaml")
4. 创建 SessionManager（含 system prompt）
5. 创建 LlmProvider（通过 provider type 选择工厂方法）
6. 主循环:
   a. 从 InputPanel 读取用户输入
   b. 若输入为 "/exit" 则退出
   c. 若输入为 "/clear" 则 session.clear(), chatPanel.clear()
   d. 若输入为 "/help" 则显示帮助信息
   e. 否则:
      - session.addUserMessage(input)
      - chatPanel.appendUserMessage(input)
      - provider.streamChat(session.getHistory(), config)
      - 遍历 StreamEventIterator:
          ContentDelta   -> chatPanel.appendContent(delta)
          ThinkingDelta  -> chatPanel.appendThinking(delta)
          StreamComplete -> session.addAssistantMessage(accumulated)
          StreamError    -> chatPanel.showError(error)
      - 回到 (a)
```

---

## 四、测试策略

### 4.1 三层测试体系

```
┌──────────────────────────────────────────────────────────┐
│               Integration Tests (MockWebServer)           │
│  验证完整 HTTP 请求/响应周期 + SSE 流解析                 │
│  AnthropicProviderIT / OpenAIProviderIT                   │
├──────────────────────────────────────────────────────────┤
│               Contract Tests                              │
│  确保所有 Provider 实现遵循相同的行为契约                  │
│  LlmProviderContractTest<T extends LlmProvider>           │
├──────────────────────────────────────────────────────────┤
│               Unit Tests (JUnit 5 + Mockito)              │
│  孤立测试各组件内部逻辑                                    │
│  ConfigLoaderTest / SseParserTest /                       │
│  AnthropicProviderTest / OpenAIProviderTest /             │
│  InMemorySessionManagerTest                               │
└──────────────────────────────────────────────────────────┘
```

### 4.2 TDD 执行顺序

```
Step 1: ConfigLoaderTest          (单元测试)
Step 2: SseParserTest             (单元测试)
Step 3: AnthropicProviderTest     (单元测试 + Mockito)
Step 4: OpenAIProviderTest        (单元测试 + Mockito)
Step 5: LlmProviderContractTest   (契约测试)
Step 6: InMemorySessionManagerTest(单元测试)
Step 7: TUI 组件测试              (单元测试)
Step 8: AnthropicProviderIT       (集成测试 + MockWebServer)
Step 9: OpenAIProviderIT          (集成测试 + MockWebServer)
```

### 4.3 Mock 依赖表

| 测试类 | Mock 目标 | Mock 工具 | 验证点 |
|--------|-----------|-----------|--------|
| `ConfigLoaderTest` | 文件系统 | java.nio.file.Files + JUnit TempDir | YAML 解析、校验错误 |
| `SseParserTest` | `InputStream` | ByteArrayInputStream | 多行 data、注释、空行 |
| `AnthropicProviderTest` | `OkHttpClient` | Mockito | 请求体构建、头、超时 |
| `OpenAIProviderTest` | `OkHttpClient` | Mockito | 请求体构建、system 分离 |
| `InMemorySessionManagerTest` | 无 | 纯 JUnit | 防御性副本、clear |
| `AnthropicProviderIT` | HTTP 端点 | MockWebServer | 完整 SSE 流、错误码 |
| `OpenAIProviderIT` | HTTP 端点 | MockWebServer | 完整 SSE 流、[DONE] |

---

## 五、数据流

### 5.1 完整请求-渲染链路

```
┌──────────┐   user input    ┌──────────────────┐
│          │ ──────────────> │                  │
│  TUI     │                 │  SessionManager  │
│  Input   │                 │  .addUserMessage │
│  Panel   │                 │                  │
│          │                 └───────┬──────────┘
└──────────┘                         │ List<Message>
                                     ▼
                            ┌──────────────────┐
                            │  LlmProvider     │
                            │  .streamChat()   │
                            └───────┬──────────┘
                                    │ POST (JSON)
                                    ▼
                            ┌──────────────────┐
                            │  OkHttp Client   │
                            │  (HTTP/2)        │
                            └───────┬──────────┘
                                    │ Response Body (InputStream)
                                    ▼
                            ┌──────────────────┐
                            │  SseParser       │
                            │  .parse()        │
                            └───────┬──────────┘
                                    │ StreamEventIterator
                                    ▼
                            ┌──────────────────┐
                            │  TUI ChatPanel   │
                            │  for-each 消费    │
                            │  switch(event)   │
                            │   ├ ContentDelta ──> appendContent()
                            │   ├ ThinkingDelta ─> appendThinking()
                            │   ├ StreamComplete -> addAssistantMessage()
                            │   └ StreamError ───> showError()
                            └──────────────────┘
                                    │
                                    ▼
                            ┌──────────────────┐
                            │  SessionManager  │
                            │ .addAssistantMsg │
                            └──────────────────┘
                                    │
                                    ▼
                            ┌──────────────────┐
                            │  wait for next   │
                            │  user input      │
                            └──────────────────┘
```

### 5.2 数据处理管线

```
byte[] (HTTP body)
   │
   ▼
InputStream (BufferedSource.inputStream())
   │
   ▼
BufferedReader (UTF-8, SSE line-by-line)
   │
   ├── data: {...json...} ──> StringBuilder buffer
   ├── data: {...more...} ──> StringBuilder buffer (append)
   ├── (empty line) ────────> DataCallback.onData(buffer.toString())
   │                            │
   │                            ├── "text_delta"     ──> ContentDelta
   │                            ├── "thinking_delta" ──> ThinkingDelta
   │                            ├── stop/finish      ──> StreamComplete
   │                            └── parse error      ──> StreamError
   │
   └── EOF ──────────────────>  hasNext() = false
```

---

## 六、工程决策记录

### D-01 -- 构建工具：Maven

| 属性 | 值 |
|------|-----|
| **决策** | 使用 Maven 作为构建工具 |
| **理由** | 团队对 Maven 最熟悉；maven-surefire-plugin + maven-failsafe-plugin 原生支持单元测试和集成测试分离；Maven Central 生态成熟。 |
| **备选** | Gradle（构建速度更快但团队学习成本高） |
| **日期** | 2026-06-15 |

### D-02 -- TUI 框架：Lanterna

| 属性 | 值 |
|------|-----|
| **决策** | 使用 Google Lanterna 作为终端 UI 框架 |
| **理由** | 纯 Java 实现，不依赖 JNI 或 native 库，Windows/Linux/macOS 行为一致；支持 256 色、流式文本渲染；Screen + TextGUI 模型适合复杂布局。 |
| **备选** | java.io.Console（太原始）、JLine（无 GUI 布局）、Charm（Go 生态） |
| **日期** | 2026-06-15 |

### D-03 -- HTTP 客户端：OkHttp 4.x

| 属性 | 值 |
|------|-----|
| **决策** | 使用 OkHttp 4.x 作为 HTTP 客户端 |
| **理由** | 连接池复用减少延迟；Interceptor 可插拔（日志、重试、限流）；`readTimeout(0)` 支持 SSE 无限读超时；MockWebServer 作为集成测试工具。 |
| **备选** | java.net.HttpClient（无 MockWebServer 等价物）、Apache HttpClient（API 冗长） |
| **日期** | 2026-06-15 |

### D-04 -- 配置格式：YAML + Jackson

| 属性 | 值 |
|------|-----|
| **决策** | 使用 YAML 配置文件 + Jackson YAML 反序列化 + Jakarta Validation 校验 |
| **理由** | YAML 对人可读性优于 JSON/Properties；Jackson 注解驱动，`record` 类型天然匹配；Jakarta Validation 提供声明式约束校验。 |
| **备选** | TOML（生态弱）、HOCON（Typesafe Config，学习成本高） |
| **日期** | 2026-06-15 |

### D-05 -- 测试策略：分层 TDD

| 属性 | 值 |
|------|-----|
| **决策** | 三层测试体系：单元测试 -> 契约测试 -> 集成测试，按自底向上 TDD 顺序执行 |
| **理由** | 契约测试确保多 Provider 行为一致；MockWebServer 模拟真实 HTTP 避免外部依赖；TDD 顺序保证每层有完整测试覆盖再进入下一层。 |
| **备选** | 全部集成测试（运行慢、定位难） |
| **日期** | 2026-06-15 |

### D-06 -- 包结构：Mixed-layer

| 属性 | 值 |
|------|-----|
| **决策** | 使用 mixed-layer 包结构，按功能域垂直分层而非严格水平分层 |
| **理由** | chat/ 和 core/ 分离业务与基础设施，但内部按 provider/model/config 水平组织；比 strict layered 减少跨包跳转，比 strict feature-based 保持基础设施复用。 |
| **备选** | Strict layered (core -> service -> tui, 过于抽象)、Strict feature-based (anthropic/ openai/ 重复 config/sse) |
| **日期** | 2026-06-15 |

### D-07 -- 流式模型：自定义 Iterator

| 属性 | 值 |
|------|-----|
| **决策** | 使用 `StreamEventIterator extends Iterator<StreamEvent>, AutoCloseable` 作为流式事件的统一消费模型 |
| **理由** | 标准的 `Iterator` 语义让 TUI 可以用 `while(iter.hasNext()) { event = iter.next(); }` 统一消费；`AutoCloseable` 确保 InputStream 在流结束或异常时关闭；相比 Reactor/Flow API 减少学习成本。 |
| **备选** | java.util.concurrent.Flow (Reactive Streams, 过于复杂)、CompletableFuture<Stream<StreamEvent>> (惰性不足) |
| **日期** | 2026-06-15 |

### D-08 -- 流式事件：Sealed Interface

| 属性 | 值 |
|------|-----|
| **决策** | 使用 `sealed interface StreamEvent` 限定事件种类为 4 种 |
| **理由** | JDK 17 sealed 特性允许编译期穷举检查，`switch` 表达式无需 default 分支；事件种类明确（ContentDelta / ThinkingDelta / StreamComplete / StreamError），后期扩展只需新增 permit 子类。 |
| **备选** | 抽象类（可扩展性差）、枚举（无法携带数据）、String + Map（类型不安全） |
| **日期** | 2026-06-15 |

---

## 附录

### A. 术语表

| 术语 | 说明 |
|------|------|
| SSE | Server-Sent Events，HTTP 长连接上的事件流协议 |
| TUI | Terminal User Interface，终端用户界面 |
| TDD | Test-Driven Development，测试驱动开发 |
| Provider | LLM 服务提供商（Anthropic / OpenAI） |
| StreamEvent | 流式事件，SSE 解析后的统一事件类型 |
| Thinking | Anthropic 的"思考"过程，在最终回答前展示推理步骤 |

### B. 参考资料

- [PRD: LavenderCode v1.0](../../current/modules/LavenderCode/2026-06-01-lavendercode-v1-prd.md)
- [Anthropic Messages API](https://docs.anthropic.com/en/api/messages)
- [OpenAI Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [OkHttp SSE Recipes](https://square.github.io/okhttp/recipes/#events)
- [Lanterna Documentation](https://github.com/mabe02/lanterna)
