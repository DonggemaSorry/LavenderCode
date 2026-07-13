# 项目记忆与会话持久化 — 设计文档

> 日期：2026-07-13  
> 状态：实现已完成（分支 `feat/ch09-project-memory`）  
> 关联 PRD：`docs/current/modules/memory/PRD_项目记忆与会话持久化.md`  
> 关联计划：`docs/superpowers/plans/2026-07-13-project-memory-session-persistence.md`  
> 实现策略：方案 1 — 装饰 `SessionManager` + 独立服务；类型名对齐现有 LavenderCode

---

## 1. 背景与目标

LavenderCode 在 ch08 解决了单进程内长时间工作的上下文治理，但进程退出后对话与工作上下文丢失。本设计实现跨会话记忆：

1. **项目指令**（静态规范）：三层 `LAVENDERCODE.md` + `@include`
2. **会话存档**（工作记忆）：JSONL 追加 + `/resume` 恢复
3. **自动笔记**（长期记忆）：条件触发、异步更新、索引注入

**成功标准**：对齐 PRD G1–G8 与 AC1–AC29；对 `ReActLoop` / 压缩主路径侵入最小。

**明确不做**：向量库/RAG、团队同步、启动自动恢复、会话合并、指令热更新、笔记全文搜索、清理旧格式 session ID。

---

## 2. 决策记录

| 议题 | 选择 | 说明 |
|---|---|---|
| Spec 类型名 vs 代码 | **以代码为准改写** | `Conversation`→`SessionManager`；`replaceMessages`→`replaceHistory`；`Done`→`AgentEvent.Complete`；`SessionContext`→`SessionHandle`+`SessionPaths` |
| `/resume` UI | **JLine 轻量列表** | 不引入 Lanterna；↑↓ + 字符过滤 + Enter/Esc |
| 配置 `system_prompt` vs 文件指令 | **分模块** | 配置 priority 8；`LAVENDERCODE.md` priority 9；记忆 priority 10 |
| 模块 priority 数字 | **延续小整数** | 不采用 Spec 的 80/100 |
| 目录名 | **`.lavendercode`** | 与现有工程一致 |
| 架构路径 | **方案 1** | 装饰器 + 独立服务，非 SessionRuntime 大重构 |
| `/clear` 与 JSONL | **清内存，继续写同一文件** | 不旋转新 session、不删磁盘目录 |

---

## 3. 架构与组件

```text
LavenderCode.main
  ├─ InstructionLoader              → 缓存指令文本
  ├─ MemoryService.loadIndex        → 缓存记忆索引
  ├─ SessionCleanup (后台 VT)       → 清理 30 天+ 新格式目录
  ├─ ContextBootstrap.create        → SessionHandle(sessionId, SessionPaths, ContextManager)
  └─ PersistingSessionManager       → 包装 InMemory + SessionTranscriptWriter
         ↓
TerminalChatApplication / NetworkOrchestrator
  ├─ SystemPromptAssembler.assemble(config, fileInstructions, memoryIndex)
  ├─ /resume → SessionPicker → SessionRestorer → 切换 Handle + Writer
  └─ AgentEvent.Complete → MemoryService.maybeUpdateAsync(...)
```

| 组件 | 包 | 职责 | 非职责 |
|---|---|---|---|
| `InstructionLoader` | `core.prompt` | 三层扫描、`@include`、进程内缓存 | 热更新 |
| `SystemPromptAssembler` | `core.prompt` | `assemble(config, instructions, memory)` | 读磁盘 |
| `SessionIdGenerator` | `core.context` | `YYYYMMDD-HHMMSS-xxxx` | — |
| `SessionHandle` | `core.context` | 暴露并可切换 sessionId / `SessionPaths` | 消息内容 |
| `SessionTranscriptWriter` | `chat.session` | JSONL 追加、compact 行、刷盘、`Closeable` | 恢复解析 |
| `PersistingSessionManager` | `chat.session` | 装饰 `SessionManager`，变更后写盘 | 业务编排 |
| `SessionCatalog` | `chat.session` | 扫描有效会话、列表元数据 | UI |
| `SessionRestorer` | `chat.session` | 恢复流水线 | TUI |
| `SessionPicker` | `chat.terminal` | JLine 选择交互 | 文件 IO |
| `MemoryService` | `core.memory` | 索引、异步更新、目录锁 | 修改 conversation |
| `SessionCleanup` | `chat.session` | 启动后台清理 | 旧 ID |

### Spec → 代码映射

| Spec | LavenderCode |
|---|---|
| `Conversation` | `SessionManager` / `PersistingSessionManager` |
| `replaceMessages` | `replaceHistory` |
| `Done` | `AgentEvent.Complete` |
| `SessionContext` | `SessionHandle` + `SessionPaths` |
| `SessionState.RESUMING` | Orchestrator/Input 门闩（布尔或小型枚举即可） |
| `compact/SessionContext.newSessionId` | `SessionIdGenerator.generate` |
| priority 80 / 100 | 9 / 10 |

---

## 4. 数据流

### 4.1 启动

1. `InstructionLoader.load(projectRoot)` → 失败降级空串；结果缓存至进程结束  
2. `MemoryService.loadIndex(projectRoot)` → 项目级在前、用户级在后；拼接 >25KB 截断并标注 `(index truncated)`  
3. `SessionCleanup.startBackground(sessionsRoot)` → virtual thread  
4. `ContextBootstrap.create(...)` → 新 ID 格式；创建 `.lavendercode/sessions/<id>/` 与 `tool-results/`；返回 `SessionHandle`  
5. `PersistingSessionManager(inner, writer)`  
6. 将 instructions / memoryIndex / handle 注入 `TerminalChatApplication`  
7. **始终开新会话**（不自动 resume）

每轮用户消息发送前：

```text
SystemPromptAssembler.assemble(
  options.systemPrompt(),   // priority 8，可空跳过
  cachedInstructions,       // priority 9
  memoryService.currentIndex() // priority 10
)
```

固定模块 1–7 不变。

### 4.2 对话追加

| `SessionManager` 操作 | JSONL 行为 |
|---|---|
| `addUserMessage` / `addAssistantMessage` / `addToolMessages` | 追加对应消息行；**会话第一条消息**携带 `model` |
| `replaceHistory` | 先追加 `{"type":"compact","ts":...}`，再逐条追加新历史 |
| `updateToolContent` / `removeLastMessages` | 本章不改写已落盘历史（内存修正即可）；若实现期发现恢复一致性问题，在 plan 中补「不写盘」或「追加修正」说明 |
| `clear` | 不删除磁盘会话目录；仅清内存；后续消息继续追加到**当前** JSONL |

**JSONL 消息字段**：`role`（必需）、`content`、`tool_calls`、`tool_results`、`ts`（Unix 秒，必需）、`model`（仅首条）。

**Writer**：`ReentrantLock`；append 后 flush/force；进程退出 `close()`。写失败只记日志。

**路径**：

- 会话目录：`<project>/.lavendercode/sessions/<session_id>/`
- JSONL：`conversation.jsonl`
- 工具结果：`tool-results/`（ch08 不变，仅随 ID 格式与 Handle 切换）

### 4.3 `/resume`

前置：仅空闲；Agent 运行中 → 提示「请等待当前任务完成」。

1. 置 `RESUMING`，禁止 `SendMessage`→`ReActLoop.run`  
2. `SessionCatalog.list()`：子目录含 `conversation.jsonl` **且** ID 匹配 `^\d{8}-\d{6}-[0-9a-f]{4}$`；按 `conversation.jsonl` mtime 倒序  
3. `SessionPicker`：展示标题（首条 user content，截断 50+省略号）、相对时间、model、文件大小；↑↓ / 过滤 / Enter / Esc  
4. `SessionRestorer.restore`：  
   - 从**最后一个** `compact` 标记之后加载  
   - 坏行静默跳过  
   - 末条 assistant 含 `tool_calls` 且无后续 tool → 截断到该 assistant **之前**  
   - 估算 token > `ContextWindowDefaults.autoCompactThreshold(contextWindow)` → 调用现有手动压缩路径一次；压缩失败则带已加载历史进入空闲并打日志  
   - 末条 `ts` 距今 > 6h → 追加时间跨度提醒（user 消息，文案见 PRD），并写入 JSONL  
   - `replaceHistory`；关闭旧 Writer；以追加模式打开目标 Writer；`SessionHandle` 切换到恢复的 id/paths  
5. 系统消息：`已恢复会话 <id>，共 <N> 条消息`；清除 `RESUMING`  
6. 启动时新建的短/空会话目录**保留不删**

### 4.4 记忆更新

触发（`AgentEvent.Complete` 之后，或关系）：

1. `turnCount % 5 == 0`（Orchestrator 维护 turnCount，每次 Complete +1）  
2. 本轮用户消息含关键词：`记住`、`记忆`、`别忘`、`remember`、`memo`（英文大小写不敏感）

执行：

- virtual thread 异步；主路径立即接受下一输入  
- 输入：最近一轮（最后一条 user → 最终 assistant）+ 两级现有索引；**不传工具定义**  
- 输出：JSON 数组 `create` / `update` / `delete`；`[]` = 无更新  
- 文件：项目级 `.lavendercode/memory/`；用户级 `~/.lavendercode/memory/`；索引 `MEMORY.md`  
- 笔记类型：`user_preference` | `correction_feedback` | `project_knowledge` | `reference_material`  
- 文件名：`<type>_<slug>.md`；frontmatter：type/title/created/updated  
- 去重交 LLM；目录写操作加 `ReentrantLock`  
- 成功后刷新内存 index 缓存  
- 失败：日志，不重试  
- 与 `/compact` 可并发（只读快照、只写 memory）

---

## 5. 指令装载细则

扫描顺序（高优先级在前拼接，层间空行）：

1. `<project_root>/LAVENDERCODE.md`  
2. `<project_root>/.lavendercode/LAVENDERCODE.md`  
3. `~/.lavendercode/LAVENDERCODE.md`  

`@include`：

- 仅独占行：`@include <relative_path>`  
- 相对当前文件目录；可嵌套  
- 最大深度 5（入口为第 1 层）  
- `visited` 绝对路径防环  
- 根边界：①② ∈ `projectRoot`；③ ∈ `~/.lavendercode/`  
- 缺失静默跳过；空文件空内容；前 512 字节含 `\x00` → 跳过并警告  
- 警告注释格式与 PRD/Spec 一致  

---

## 6. 错误处理与并发

见第 4 节各路径降级策略，汇总：

| 场景 | 策略 |
|---|---|
| 指令失败 | 空指令，不阻塞启动 |
| JSONL 写失败 | 日志，对话继续 |
| 恢复单点错误 | 逐步降级，不拖垮整次恢复 |
| 记忆失败 | 静默跳过 |
| 清理单目录失败 | 跳过 |
| Writer / Memory | 各自锁；VT 后台任务 |

向后兼容：无指令/无 memory/旧 session ID 不影响启动；旧 ID 不展示不清理；纯 `InMemorySessionManager` 测试路径不变。

---

## 7. 测试策略

| 层 | 覆盖 |
|---|---|
| `InstructionLoader` | AC1–AC6 |
| Writer + 装饰器 | AC7–AC10、AC28 |
| `SessionRestorer` / Catalog | AC11–AC20（Picker 交互可用窄集成或驱动测试） |
| `MemoryService` | AC21–AC26（Provider mock） |
| Assembler | AC27 |
| 互斥 | AC29 |

核心逻辑脱离真实 Provider；N1 性能可用限时夹具，不挡功能合入。

---

## 8. 实现分期

1. **Session 身份与 JSONL**：ID 格式、`SessionHandle`、Writer、`PersistingSessionManager`、compact 标记、Bootstrap/main 接线  
2. **指令与 Assembler**：`InstructionLoader`、三参数 assemble、启动缓存注入  
3. **恢复与清理**：Catalog、Restorer、Picker、`/resume` 命令与互斥、SessionCleanup  
4. **记忆**：`MemoryService`、Complete 钩子、索引注入刷新  

每期结束后应能独立演示/单测对应 AC 子集。

---

## 9. 主要改动面（文件级预期）

- 新增：`core.prompt.InstructionLoader`；`core.memory.*`；`chat.session.PersistingSessionManager`、`SessionTranscriptWriter`、`SessionCatalog`、`SessionRestorer`、`SessionCleanup`；`chat.terminal.SessionPicker`；`core.context.SessionHandle`  
- 修改：`SessionIdGenerator`；`SessionPaths`（若需随 Handle 切换根）；`ContextBootstrap` 返回 Handle；`SystemPromptAssembler`；`LavenderCode.main`；`NetworkOrchestrator`（assemble 参数、Complete 钩子、`/resume`、门闩、turnCount）；`BuiltinCommandRegistry` / `InputEvent.CommandType`；`TerminalChatApplication` 构造注入  
- 测试：上述组件单测 + 少量编排互斥测试  

---

## 10. 实现期可选细节（不改变对外行为）

1. `updateToolContent` / `removeLastMessages`：首期允许内存与已落盘 JSONL 短暂不一致；权威恢复仍以 compact 之后的 JSONL 为准。  
2. `SessionPicker`：在「InputSystem 原始键模式」与「临时占用输入循环」中择一对现有 TUI 侵入更小的实现；对外交互仍为 ↑↓ / 过滤 / Enter / Esc。  
