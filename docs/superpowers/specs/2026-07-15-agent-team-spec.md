# Agent Team Spec

> 版本：v1.0 · 状态：草稿 · 适用迭代：Agent Team 一期（ch15）  
> 对应产品概要：[PRD_TeamLead.md](../../current/modules/agent-team/PRD_TeamLead.md)
>
> **命名对齐说明（强制）**：本仓库一律使用 **LavenderCode** / **`.lavendercode`** / **`com.lavendercode`** / 环境变量 **`LAVENDERCODE_COORDINATOR_MODE`**。禁止使用教学稿中的 `mewcode` / `.mewcode` / `MEWCODE_*` / `dev.mewcode`。
>
> **工具命名对齐（相对教学稿）**：
> - 协作工具加 `Team` 前缀，避免与 ch13 后台任务工具撞名：`TeamTaskCreate` / `TeamTaskGet` / `TeamTaskList` / `TeamTaskUpdate` / `TeamSendMessage`；既有 `TaskList` / `TaskGet` / `TaskStop` / `SendMessage`（后台续派）**不改名**。
> - 读/搜/命令类对齐现网：`read_file` / `search_file` / `search_content` / `execute_command`（教学稿中的 glob / grep / bash）。
> - 建组/删组工具名保持：`TeamCreate` / `TeamDelete`。
> - CLI 入口：`lavendercode`（子进程队员模式：`lavendercode --team-member ...`）。

## 背景

ch13 SubAgent 把任务从单 Agent 委派给子 Agent，实现了消息、权限账本、文件读缓存与 token 计数的隔离；ch14 Worktree 给每个子 Agent 配上独立工作目录，文件系统层并发也安全。但这两章合起来仍是「星型」拓扑——所有子 Agent 只能与主 Agent 通信，子 Agent 之间没有横向通道；主 Agent 既要决策、又要中转，既是大脑也是邮局。对「同时重构四个模块」「三个角度查同一个 bug」这类持续性、需要互相交流的工作，星型结构的瓶颈很明显。

本章把 LavenderCode 从星型升级到「网状」：

- 主 Agent 创建 **Team** 后升任 **Lead**，Team 是一个长期存在的小组对象，记名称、负责人、成员花名册、持久化位置
- 每个 **队员**（Teammate）是一个独立的 Agent 实例，有自己的 Conversation、自己的 Worktree
- 三种执行后端 `tmux` / `iterm2` / `in-process` 覆盖不同环境；按优先级一次性自动检测，启动后不静默回退
- 队员之间通过**共享任务列表**与**邮箱**直接通信，不必经过 Lead 中转；协作工具仅在 Team 上下文出现
- 队员可暂停可续写，自然停下后 session 留盘，Lead 调 `TeamSendMessage` 会从磁盘恢复后继续指派
- Lead 可选启用 **Coordinator Mode**（独立于 Team，但典型场景一起用），双锁机制下剥夺 `write_file` / `edit_file`，只保留调度、读类操作与 `execute_command`（用于 git merge）
- 收敛阶段由 Lead 用 `execute_command` 跑 `git merge` 逐个合各队员的 worktree 分支，冲突由 LLM 推理解决，搞不定就 `git merge --abort` 保留 worktree 上报用户

LavenderCode 现有相关基础设施：

- ch13 `TaskManager` 已支持后台任务管理 + `SendMessage` 续派 + 按 name 查找；本章扩展为多 Team 寻址（新增 `AgentNameRegistry`）
- ch13 `AgentTool.execute` 已是子 Agent 启动入口，本章新增 `teamName` 参数走 Team spawn 分支
- ch13 `ToolFilter` 已支持过滤；本章新增 Team 专属白名单（协作工具）与 Coordinator Mode 白名单
- ch14 `WorktreeManager` 已支持嵌套 slug（`/` → `+`），本章复用做队员 worktree（slug 形式 `team-<teamName>/<member>`）
- ch12 session 持久化（`.lavendercode/sessions/<id>/conversation.jsonl`）按对话粒度落盘；本章给每个队员单独申请一个 session，队员 stop 不删 session，`TeamSendMessage` 续派时通过 session 反序列化 Conversation
- ch10 slash 命令系统，本章新增 `/team` 系列
- ch11 `permission` 已支持 `plan` 模式，本章给 `planModeRequired` 队员的 Plan 提交–Lead 审批工作流套用同一引擎

本章**只做**到「Lead 多人协作 + Plan 审批 + Coordinator 收敛」。跨进程跨机器分布式团队、队员之间实时流式通信、复杂任务依赖约束（优先级 / deadline）、Windows 平台 iTerm2 适配均不在范围内。

## 目标

- **G1**: 提供 `Team` 与 `TeamManager`——Team 封装小组生命周期（name、leadAgentId、members、configPath）；Manager 在单 LavenderCode 进程内管理多个 Team（典型场景同时只有一个活跃 Team）
- **G2**: 提供 `TeamCreate` 工具——主 Agent 调用即创建 Team、调 `detectBackend` 确定后端、写 `~/.lavendercode/teams/<sanitizedName>/config.json`、把 Lead 注册成第一个成员；同名团队自动后缀 `-2` / `-3` 避免冲突
- **G3**: 扩展 `Agent` 工具——增加 `teamName` 可选参数，非空时走 Team spawn 分支：加载定义 → 创建队员 Worktree → 注入协作工具 → 按后端分流 spawn → 注册到 `AgentNameRegistry` → 写入 `team.members`
- **G4**: 提供 `TeamDelete` 工具——确认所有成员 `isActive=false` 后，删队员 worktree + 删 team 目录，Lead 退出团队；有活跃成员时拒绝删除
- **G5**: 三种执行后端 `tmux` / `iterm2` / `in-process`，统一抽象 `Backend` 接口；`detectBackend` 按 `$TMUX → $TERM_PROGRAM=iTerm.app && which it2 → which tmux → in-process` 优先级一次性决定，不做运行时回退
- **G6**: 队员注入 5 个协作工具 `TeamTaskCreate` / `TeamTaskGet` / `TeamTaskList` / `TeamTaskUpdate`（后者支持 `addBlocks` / `addBlockedBy` 依赖字段） / `TeamSendMessage`；主 Agent 与普通 SubAgent 看不到这些工具
- **G7**: `TeamSendMessage` 寻址支持 `to="<name>"`、`to="<agentId>"`、`to="*"` 广播三种；通过 `AgentNameRegistry` 解析 name → agentId，写邮箱；Tmux/iTerm2 后端额外通过 `send-keys` 唤醒目标 pane
- **G8**: 邮箱文件并发安全——每个收件人独占一个 lock 文件（`StandardOpenOption.CREATE_NEW`），抢锁失败按 5-100ms 随机抖动重试，最多 10 次；持锁超过 10 秒视为 stale 直接清掉；消息文件 read-modify-write，走 `Files.move(...,ATOMIC_MOVE)` 原子替换
- **G9**: 三种结构化消息——纯文本（必带 5-10 词 `summary`）、`shutdown_request` / `shutdown_response`（优雅退出协商）、`plan_approval_response`（Plan 审批回复，只允许 Lead 发送）；全部走同一 `TeamSendMessage` 入口，以 `type` 字段分流
- **G10**: 队员收到的未读消息在下一轮 Agent Loop 开头被读出，以 `<incoming-messages>` system reminder 形式注入到 LLM 输入；读后批量标记为 read
- **G11**: 队员 spawn 两种路径——指定 `subagentType` 走定义式（从空白对话起步）、留空走 Fork 路径（继承 Lead 完整对话历史）；Fork 路径受 `FORK_TEAMMATE` feature flag 控制，默认关闭
- **G12**: 队员 `runToCompletion` 结束后自动通知 Lead——团队 config 里把该成员 `isActive=false`、Lead 邮箱收到 idle 通知；队员的 Conversation 已通过 ch12 Writer 实时写入 session 文件
- **G13**: 队员续写——Lead 调 `TeamSendMessage(to="alice", message="…")`，系统检测 alice 已 stop 时，从 ch12 session 反序列化 Conversation、新建一条 virtual thread 走 `runToCompletion(initialMessage=newMessage)`；Conv 沿用历史
- **G14**: `planModeRequired:true` 的队员被 spawn 时强制以 plan 模式起步——计划生成后通过 `TeamSendMessage` 发给 Lead，Lead 用 `plan_approval_response` 回复 approve 或 reject；approve 时队员权限模式切到 Lead 的当前模式继续执行，reject 时队员按 feedback 调整后重新提交
- **G15**: Coordinator Mode 独立于 Team——`Coordinator.isEnabled() = feature(COORDINATOR_MODE) && envTruthy(LAVENDERCODE_COORDINATOR_MODE)`，两把锁全开才生效；开启后 Lead 工具集收窄到 `Agent / TeamCreate / TeamDelete / TeamTaskCreate / TeamTaskGet / TeamTaskList / TeamTaskUpdate / TeamSendMessage / read_file / search_file / search_content / execute_command`（剥夺 `write_file` / `edit_file`），并注入 coordinator 系统提示词引导 Research / Synthesis / Implementation / Verification 四阶段
- **G16**: 收敛全部由 LLM 推理驱动——Lead 用 `execute_command` 跑 `git merge worktree-team-<team>+<member> --no-ff -m "merge: <member>"` 逐个合，冲突由 Lead 用 Read / Edit / Shell 自行解决；搞不定就 `git merge --abort`，保留队员 worktree，把冲突上下文上报给用户
- **G17**: 提供 TUI slash 命令 `/team list` / `/team info <name>` / `/team delete <name>` / `/team kill <member>`，辅助用户人工介入
- **G18**: 与 ch04~ch14 既有功能协同——主 Agent 平时（未 TeamCreate）看到的工具列表不变（`TeamCreate`/`TeamDelete` 始终可见；协作五件套不可见）；ch13 后台任务 / AdoptRunning / `SendMessage` 续派路径保留，Team 队员的续派复用同一套底层 `TaskManager`

## 功能需求

### Team 数据结构与 Manager

- **F1**: `Team` 字段——`name`（原始名）、`sanitizedName`（经 `sanitize` 处理后用于路径）、`leadAgentId`、`members List<TeammateInfo>`、`configDir`（`<homeDir>/.lavendercode/teams/<sanitizedName>/`）、`configPath`（`<configDir>/config.json`）、`createdAt Instant`、`backend BackendType`
- **F2**: `TeammateInfo` 字段——`name`（Lead 分配的队员名，Team 内唯一）、`agentId`（对应 `BackgroundTask.id`）、`agentType`（使用的 subagent 定义名；Fork 路径下为 `""`）、`model`（覆盖，空表 inherit）、`worktreePath`（绝对路径）、`branch`（对应 worktree 分支名）、`backendType`（可 per-member 不同）、`paneId`（tmux pane / iterm2 split id，in-process 为空）、`isActive Boolean`（`null` 或 `true` 表活跃，`false` 表空闲；终止后直接从 `members` 移除）、`planModeRequired boolean`、`sessionDir`（队员独立 session 目录绝对路径）
- **F3**: `TeamManager` 字段——`lock ReentrantLock`、`teams Map<String,Team>`（按 `sanitizedName` 索引）、`homeDir`（`System.getProperty("user.home")`）、`worktreeManager`、`taskManager`、`registry AgentNameRegistry`
- **F4**: `TeamManager(Path homeDir, WorktreeManager wt, TaskManager taskMgr, AgentNameRegistry reg)`——校验 `<homeDir>/.lavendercode/teams/` 可写；扫描该目录还原 `teams` map（每个子目录读一次 `config.json`，跳过解析失败的并 stderr 警告）
- **F5**: `TeamManager.create(name, agentType)`——
  1. `sanitized = sanitize(name)`（只保留 `[a-zA-Z0-9._-]`，其他替换为 `-`，首尾去 `-`，空字符串拒绝）
  2. 同名冲突时在 `sanitized` 后追加 `-2` / `-3` 直到唯一
  3. 创建 `configDir`，落 `config.json`（原子写）
  4. 调 `detectBackend()` 写入 `team.backend`
  5. 取当前 Lead Agent id（本期 Lead = 主 Agent，固定 `"lead"`）
  6. 把 Lead 注册成第一个成员（`TeammateInfo("lead","lead", ...)`，`isActive=null`）
  7. 加入 `teams` map，返回 Team
- **F6**: `TeamManager.get(name)`——按 sanitized name 查询，返回 `Optional<Team>`
- **F7**: `TeamManager.delete(name, force)`——
  1. 取 Team；不存在抛 `TeamNotFoundException`
  2. 非 force 时若有 `member.isActive != Boolean.FALSE`（包括 null 和 true）抛 `TeamHasActiveMembersException`
  3. 逐个删队员 Worktree（调 `worktreeManager.remove(...)`，失败只警告不中断）
  4. 删队员 session 目录（失败只警告）
  5. 删 `configDir`
  6. 从 `teams` map 移除
- **F8**: `Team.addMember(TeammateInfo info)`——校验 name 在 Team 内唯一；加入 `members`；持久化 `config.json`（原子写——先写 `.tmp` 再 `Files.move(...,ATOMIC_MOVE)`）
- **F9**: `Team.setMemberActive(name, active)`——更新 `isActive`，持久化
- **F10**: `Team.removeMember(name)`——从 `members` 移除，持久化

### 后端检测与抽象

- **F11**: `BackendType` 枚举，取值 `TMUX` / `ITERM2` / `IN_PROCESS`，带 `wireValue()` 返回 `"tmux"` / `"iterm2"` / `"in-process"`
- **F12**: `Backend` 接口——`type()` / `spawn(SpawnRequest)` / `wake(paneId, agentId)` / `kill(paneId, agentId)`；`SpawnResult(paneId, agentId)`
- **F13**: `SpawnRequest` 字段——`teamName`、`memberName`、`agentId`、`worktreePath`、`sessionDir`、`agentType`、`model`、`initialPrompt`、`planModeRequired`、以及 in-process 所需的子 Agent / Conversation / TaskManager 引用。对 Pane 后端，`initialPrompt` **不**走命令行——在 `backend.spawn` 调用前由 `TeamManager.spawnTeammate` 预写入目标 mailbox（类型 `text`，from `lead`），子进程启动后读 mailbox 自然拿到。
- **F14**: `Backend.detect()`——按以下优先级一次性决定：
  1. `System.getenv("TMUX") != null` → `TMUX`
  2. `"iTerm.app".equals(System.getenv("TERM_PROGRAM"))` && PATH 中存在 `it2` → `ITERM2`
  3. PATH 中存在 `tmux` → `TMUX`（外部 spawn 新 session）
  4. 否则 → `IN_PROCESS`

### tmux 后端

- **F15**: `TmuxBackend`——`spawn` 用 `tmux split-window -h -P -F "#{pane_id}" -- <cmd>`；`cmd` 为 `lavendercode --team-member --team <teamName> --member <memberName> --agent-id <agentId> --session-dir <sessionDir> --worktree <worktreePath> [--agent-type <type>] [--model <model>] [--plan-mode]`。`--agent-id` 由 Lead spawn 时生成并传入子进程。`wake`：`tmux send-keys -t <paneId> "" Enter`。`kill`：`tmux kill-pane -t <paneId>`（忽略 pane 不存在）。
- **F16**: 若当前在 tmux 会话外但本机有 tmux，spawn 走 `tmux new-session -d`；若失败回落到错误而非 in-process（不静默回退）

### iterm2 后端

- **F17**: `Iterm2Backend`——`spawn`：`it2 split --new-pane --command "<cmd>"`（cmd 与 F15 同构）；`wake`：`it2 send-text --pane <paneId> ""`；`kill`：`it2 close-pane --pane <paneId>`

### in-process 后端

- **F18**: `InProcessBackend`——`spawn` 复用 `TaskManager.launch`（`withCwd(worktreePath)`，virtual thread 跑 `runToCompletion`）；`wake` 为 no-op；`kill` 调 `TaskManager.stop(agentId)`
- **F19**: in-process 队员**只允许同步子 Agent**——其 `Agent` 工具看不到 / 拦截 `teamName`；后台子 Agent 也禁用（过滤 `runInBackground=true`）

### Pane 后端子进程的 team-member 模式

- **F19a**: `lavendercode --team-member` **不启动 TUI**，跑自治循环（`com.lavendercode.chat.terminal.TeamMemberRunner`）：
  1. 解析 CLI：`--team / --member / --agent-id / --session-dir / --worktree / --agent-type / --model / --plan-mode`（picocli）
  2. 以 worktree 为后续 IO 与权限沙箱根
  3. 构造单独的 `TeamManager`、provider、registry、permission、hook（完整复用 Lead wire，但不构造 TUI）
  4. 构造队员 Agent，`dontAsk=true`，注入 `<team-context>` 与 `TeammateContext`（含 mailbox client）
  5. stdin scanner virtual thread：tmux send-keys 回车 → `wakeQueue`，触发立刻读 mailbox（0~2s 内响应）
  6. 主循环：读未读 → 空则 `wakeQueue.poll(2, SECONDS)` → 有未读则按类型处理并 `runToCompletion` → 完成后写 Lead mailbox idle + `setMemberActive(false)` → 若 mailbox 目录已删则优雅退出
- **F19b**: 最小事件转 stdout：`TextEvent` 直打、`ToolEvent` 打 `● tool(args)`、`DoneEvent` 打分隔线、错误打 stderr。pane 内 UX 为只读日志流；回车仅作 Wake
- **F19c**: 跨进程 `config.json`：`addMember` / `setMemberActive` 在持锁后**先 `reloadFromDiskLocked` 重读 members** 再修改 + 原子 save

### TeamCreate / TeamDelete

- **F20–F21**: `TeamCreate`——参数 `teamName`（必填）、`description`（可选）、`agentType`（可选保留位）；返回 JSON `{teamName, backend, configPath}`；非 Coordinator 下 Lead 创建后不剥夺工具
- **F22–F23**: `TeamDelete`——参数 `teamName`、`force`（可选）；调 `TeamManager.delete`

### Agent 工具扩展 (teamName)

- **F24–F25**: `Agent` 增加可选 `teamName`；非空时：校验 Team 存在；主 Agent/Lead 允许；in-process 队员再 spawn 拒绝；Pane 队员不可往 Team 加人（`teamName` 屏蔽）；加载定义（留空且 `FORK_TEAMMATE` 关则 `general-purpose`）；`worktreeManager.create("team-"+sanitized+"/"+memberName, ...)`；申请 sessionDir；注入协作工具与提示；Pane 后端预写 initialPrompt 到 mailbox；`Backend.spawn`；注册 registry；加入 members（reload-before-modify）；返回成员 JSON

### 协作工具

- **F26–F29**: `TeamTaskCreate` / `TeamTaskGet` / `TeamTaskList` / `TeamTaskUpdate`（含 `addBlocks` / `addBlockedBy` / `removeBlocks` / `removeBlockedBy`）；任务 id 形如 `task_<6位 hex>`
- **F30**: `tasks.json` 落 `<teamConfigDir>/tasks.json`，文件锁 `tasks.lock`（同邮箱 lock 机制）；字段含 id/title/description/status/assignee/blockedBy/blocks/createdAt/updatedAt；`TeamTaskList` 需计算 `isReady`

### TeamSendMessage 与邮箱

- **F31**: `TeamSendMessage` 参数——`to`（必填）、`summary`（纯文本必填 5-10 词）、`message`、`type`（默认 `text`）、`payload`
- **F32**: 邮箱路径——`<teamConfigDir>/mailbox/<agentId>.json`；消息字段 from/to/type/summary/content/payload/timestamp/read
- **F33**: `Mailbox.write` / `read` / `markRead`；锁文件机制同 G8；广播对除发件人外每人写一次
- **F34**: 执行校验：调用者在 Team 内；解析 to；`plan_approval_response` 仅 Lead；`shutdown_response` 只能发给 Lead；写邮箱；Pane 则 wake；in-process 已 stop 则续写（F45）；返回 deliveredTo/timestamp

### Agent 名称注册表

- **F35–F38**: `AgentNameRegistry`——`byName` / `byId`；`register` / `unregister` / `resolve` / `nameOf`；spawn 队员与 ch13 `name` 参数统一走本 registry；后注册覆盖前注册

### 队员系统提示词与 dontAsk

- **F39**: 追加固定团队提示（须用 `TeamSendMessage` 沟通等）
- **F39a**: 所有 Team 队员一律 `dontAsk=true`，覆盖角色定义里的 `permissionMode` Ask 行为
- **F40**: 注入 `<team-context>`（team / memberName / agentId / worktree / 成员列表）

### 邮箱读取与 Lead 监视

- **F41**: 队员每轮 LLM 前读未读，注入 `<incoming-messages>` 后 `markRead`
- **F41a**: Lead 侧由 TUI 后台 `LeadMailWatcher` 轮询所有 Team 的 `mailbox/lead.json`，渲染 `<team-update>`（截断上限 8000 字符），并信号通知；Lead 长 Run 中下一轮 LLM 前可见
- **F41b**: Lead IDLE 时 `LeadMailWaiter` 自动合成 `[team-update]` 用户消息并 `beginAutonomousTurn`，避免 reminder 静默堆积
- **F42–F44**: incoming 格式；shutdown / plan_approval 处理语义

### 队员空闲与续写

- **F45–F47**: 结束后 `isActive=false` + Lead idle 消息；in-process 已 stop 时从 sessionDir 恢复并 `TaskManager` 续派；Pane 靠 mailbox+wake，pane 已死则报错

### Plan 审批工作流

- **F48–F51**: `planModeRequired` → 初始 PLAN；计划经 `TeamSendMessage` 文本发给 Lead；Lead 回 `plan_approval_response`；approve 切 DEFAULT（本期固定），reject 把 feedback 当新用户消息并重回 plan

### Coordinator Mode

- **F52**:
  ```java
  public static boolean isEnabled(Options cfg) {
      if (!Feature.has("COORDINATOR_MODE", cfg)) {
          return false;
      }
      return envTruthy(System.getenv("LAVENDERCODE_COORDINATOR_MODE"));
  }
  ```
  `Feature.has` 读配置 `features.coordinatorMode`；`envTruthy` 接受 `"1"` / `"true"` / `"yes"`（大小写不敏感）
- **F53**: 白名单常量——`Agent`, `TeamCreate`, `TeamDelete`, `TeamTaskCreate`, `TeamTaskGet`, `TeamTaskList`, `TeamTaskUpdate`, `TeamSendMessage`, `read_file`, `search_file`, `search_content`, `execute_command`
- **F54**: Lead 启动时若启用：收窄 allowed tools、追加提示词、状态栏 `[COORDINATOR]`
- **F55**: 四阶段纪律（Research / Synthesis / Implementation / Verification）；派完后禁止立刻自己探索凑时间；纯 prompt 引导，弱强制

### 收敛阶段

- **F56–F58**: 无专用 merge 工具；Lead 用 `execute_command` 逐个 `--no-ff` merge；冲突用 read/edit（非 Coordinator）或 `execute_command`（Coordinator）解决；搞不定 `git merge --abort`，保留队员 worktree 上报

### TUI Slash 命令

- **F59–F62**: `/team list` / `/team info <name>` / `/team delete <name> [--force]` / `/team kill <member>`

### 持久化与恢复

- **F63**: `~/.lavendercode/teams/<sanitizedName>/config.json` 结构（name/sanitizedName/leadAgentId/backend/description/createdAt/members[...]）；原子写；跨进程 reload-before-modify
- **F64**: 启动扫描；不自动恢复 in-process 队员（标 false）；Pane 按 paneId 探测，不在则标 false
- **F65**: 队员 session：`<projectRoot>/.lavendercode/sessions/<id>/conversation.jsonl`；Team 删除时一并删
- **F66**: `force=true` 删除顺序——kill 各后端 → 清 session/worktree → 删 configDir → 移出 map

## 非功能需求

- **N1**: 主 Agent 平时工具列表稳定——`TeamCreate`/`TeamDelete` 总是可见；`Agent.teamName` 对模型可见但仅调用时校验
- **N2**: 协作五件套仅队员上下文出现
- **N3**: 邮箱写入全后端共用文件锁
- **N4**: Team 状态变更加 `Team.lock`；Team 之间独立；`TeamManager.lock` 只护 `teams` map
- **N5**: spawn/kill 不持 `Team.lock`（避免长锁）
- **N6**: ch04~ch14 既有测试零破坏
- **N7**: 错误消息、TUI、coordinator 提示词中文；代码注释中文
- **N8**: Coordinator 进程内不可运行时解锁；取消须退出重启
- **N9**: 权限沙箱允许项目根外的系统临时目录白名单（`/tmp`、macOS `/private/tmp`）供文件类工具；`execute_command` 走 exec 类权限不受该文件沙箱约束

## 不做的事

- 跨 LavenderCode 进程的 Team 共享（同一仓库同一时刻只支持一个 LavenderCode 实例操作活跃 Team）
- 跨机器分布式 Team
- 队员之间实时流式通信（走 mailbox 文件 + 轮询/Wake，不走 socket）
- 复杂任务依赖约束（优先级、deadline、SLA）
- 任务自动分配（靠 LLM 推理领任务）
- 队员细粒度资源限额（token 上限、超时硬限制）
- Plan 审批的结构化 Plan 类型（本期 Plan 文本即消息 content）
- Windows 平台特殊适配（iTerm2 仅 macOS；tmux 在 WSL 不保证；一期以 macOS / Linux 为主）
- Coordinator Mode 的运行时解锁与重新进入
- 跨 Team 寻址
- 插件来源的 Team 后端

## 验收标准

- **AC1**: `new TeamManager(...)` 在 `~/.lavendercode/teams/` 不存在时自动创建；已有时正确扫描还原
- **AC2**: `create("refactor auth", "")` sanitize 为 `refactor-auth`，配置落地，`backend` 反映检测结果
- **AC3**: 同名二次 create 自动后缀 `-2`
- **AC4**: 非 force 删除在有活跃成员时抛 `TeamHasActiveMembersException`，目录仍在
- **AC5**: force 删除清 Worktree、session、configDir
- **AC6**: `Backend.detect()` 优先级符合 F14
- **AC7**: `Agent(teamName=...)` 落地 worktree、spawn、写入 members；不带 `teamName` 维持 ch13 原行为
- **AC8**: in-process 队员调 `teamName` 抛 `InProcessTeammateNoSpawnException`
- **AC9**: 协作五件套主 Agent 不可见、队员可见
- **AC10**: `TeamTaskCreate` 落 tasks.json；`addBlockedBy` 双向更新
- **AC11**: `TeamTaskList(status=pending)` 带 `isReady`
- **AC12**: `TeamSendMessage(to=alice,...)` 追加未读到对应 mailbox
- **AC13**: `to="*"` 广播除发件人外全员
- **AC14**: 并发 10 写同一 mailbox 全部落盘无丢失
- **AC15**: lock 超 10 秒被视为 stale 并被清掉
- **AC16**: 未读以 `<incoming-messages>` 注入并标 read
- **AC17**: 自然结束后 `isActive=false`，Lead 收到 idle summary
- **AC18**: 已 stop 的 in-process 队员可被 `TeamSendMessage` 从 sessionDir 续派
- **AC19**: `planModeRequired=true` 初始 PLAN
- **AC20**: Lead 批准后下一轮切 DEFAULT
- **AC21**: 双锁开启时 Lead 白名单生效，无 write/edit；状态栏 `[COORDINATOR]`
- **AC22**: Coordinator 关闭时 Lead 工具与 ch13 一致（write/edit 可见）
- **AC23–AC24**: tmux spawn 可见新 pane；wake 经 send-keys
- **AC25**: in-process 共享 TaskManager、独立 cwd
- **AC26**: `/team` 系列行为符合 F59–F62
- **AC27**: `mvn -q -DskipTests package`、`mvn test`、`mvn spotbugs:check` 通过
- **AC28**: tmux 端到端（建组→派生 alice→文件产物→TeamSendMessage 续作→force delete）
- **AC29**: in-process 端到端（建组→bob→idle→续派恢复上下文）
- **AC30**: `LAVENDERCODE_COORDINATOR_MODE=1` 时 `write_file` 拒绝，`execute_command` 的 git merge 允许

## 包与文件落点（实现指引，详细设计可再裁）

| 区域 | 建议包 / 位置 |
| :--- | :--- |
| Team 核心 | `com.lavendercode.core.team`（Team / TeamManager / TeammateInfo / Mailbox / Backend*） |
| Coordinator | `com.lavendercode.core.coordinator.Coordinator` |
| 名称注册表 | `com.lavendercode.core.task.AgentNameRegistry`（或 `core.team`） |
| 协作工具 | `com.lavendercode.core.tool.Team*` |
| 队员子进程 | `com.lavendercode.chat.terminal.TeamMemberRunner` + CLI 参数 |
| Lead 收信 | `com.lavendercode.chat.terminal.LeadMailWatcher` / `LeadMailWaiter` |
| Slash | `BuiltinCommandRegistrar` 增补 `/team` |
