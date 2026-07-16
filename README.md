# LavenderCode

> 薰衣草色的终端里，代码如诗。

LavenderCode 是一款运行在本地终端中的 **AI 编程助手**（Java 21 / Maven）。它通过 Anthropic 或 OpenAI 兼容 API，以 ReAct Agent Loop 多轮调用工具，完成读/写/搜/改代码、执行命令，并支持权限、Skill、MCP、记忆、子 Agent、Agent Team、Worktree 等能力。

---

## 功能一览

| 能力 | 说明 |
|------|------|
| **流式对话 TUI** | JLine 终端界面，Markdown 渲染，状态栏显示模型/模式 |
| **工具调用** | 读/写/改文件、Glob、Grep、Shell、安装 Skill |
| **Agent Loop** | 多轮「推理 → 工具 → 回灌」；只读工具可并发 |
| **计划模式** | `/plan` 只读规划，`/do` 切回执行 |
| **权限系统** | 五层闸门 + HITL；Shift+Tab 切换模式 |
| **Skills** | 文件化 SOP，注册为 `/技能名`；可从 GitHub 安装 |
| **MCP** | 接入外部 MCP 工具（stdio / HTTP） |
| **记忆与指令** | `LAVENDERCODE.md` + 自动笔记 + 会话恢复 |
| **上下文压缩** | 自动压缩 + `/compact` 手动压缩 |
| **子 Agent** | `Agent` 工具委派；后台任务管理 |
| **Agent Team** | 多队员协作；`/team` 管理 |
| **Worktree** | Git Worktree 隔离写盘；`/worktree` 管理 |
| **Hooks** | 生命周期 YAML 钩子；可拦截工具/提示 |

---

## 环境要求

- **Java 21+**
- **Maven 3.8+**
- 可用的 LLM API Key（Anthropic 或 OpenAI 兼容端点）

---

## 快速开始

```bash
# 1. 克隆
git clone https://github.com/DonggemaSorry/LavenderCode.git
cd LavenderCode

# 2. 配置
cp config.yaml.example config.yaml
# 编辑 config.yaml，填入 api_key

# 3. 编译
mvn clean package

# 4. 运行
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar

# 或
mvn exec:java

# 指定配置文件
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar --config /path/to/config.yaml

# 跳过开场动画
java -Dlavendercode.skipSplash=true -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 配置说明

主配置文件：`config.yaml`（模板见 `config.yaml.example`）。

### Provider

```yaml
providers:
  - name: deepseek              # 可选显示名
    protocol: openai            # anthropic | openai
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: your-key-here
    thinking:                   # Anthropic thinking（可选）
      enabled: false
      budget_tokens: 4000
    context_window: 128000      # 可选；省略则 Anthropic 200K / OpenAI 128K
```

配置多个 Provider 时，启动会交互选择。

### Options

| 键 | 默认 | 含义 |
|----|------|------|
| `max_tokens` | `4096` | 单次生成上限 |
| `system_prompt` | `""` | 自定义系统指令 |
| `tool_system_enabled` | `true` | 是否启用工具 |
| `command_execution_enabled` | `false` | 是否允许 Shell（谨慎开启） |
| `command_timeout_seconds` | `120` | 命令超时（秒） |
| `file_operation_timeout_seconds` | `30` | 文件操作超时 |
| `read_file_max_lines` | `2000` | 读文件行数上限 |
| `command_output_max_chars` | `30000` | 命令输出截断长度 |
| `search_max_results` | `200` | Glob/Grep 结果上限 |
| `enable_sub_agent_background` | `true` | 子 Agent 后台能力 |
| `coordinator_mode` | `false` | Coordinator 模式（需环境变量双锁） |
| `fork_teammate` | `false` | Fork 派生队友相关 |

**Coordinator 双锁：** `options.coordinator_mode: true` **且** 环境变量 `LAVENDERCODE_COORDINATOR_MODE` 为真值。

---

## 终端操作

### 快捷键

| 操作 | 按键 |
|------|------|
| 发送消息 | Enter |
| 换行 | Alt+Enter（部分终端也可用 Ctrl+J） |
| 取消当前请求 / HITL 拒绝 | Esc |
| 切换权限模式 | **Shift+Tab** |
| 退出 | Ctrl+D（空输入）或 `/exit` |
| `/` 命令补全 | ↑↓ 选择，Enter/Tab 确认，Esc 取消 |

### 内置斜杠命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/help` | | 显示命令列表 |
| `/clear` | | 清空对话并开启新会话 |
| `/compact` | | 手动压缩上下文 |
| `/exit` | `/quit` | 退出 |
| `/plan` | | 进入计划模式（仅只读工具） |
| `/do` | | 退出计划模式并注入「按计划执行」 |
| `/resume` | | 从历史会话恢复 |
| `/review` | | 请求 AI 审查近期代码变更 |
| `/session` | | 显示当前会话 ID / 存档路径 |
| `/status` | | 模式、Token、工具数、记忆、模型等 |
| `/memory` | | 已加载记忆文件列表 |
| `/permission` | | 当前权限模式 |
| `/hooks` | | 已加载 Hook 规则 |
| `/worktree` | | 管理 Git Worktree（见下文） |
| `/team` | | 管理 Agent Team（见下文） |

Skills 会额外注册为 `/<skill-name>`（帮助里带 `[skill]` 标记）。

---

## 工具能力

`tool_system_enabled: true` 时，Agent 可调用：

| 工具名 | 作用 |
|--------|------|
| `read_file` | 读取文件（带行号） |
| `write_file` | 写入 / 创建文件 |
| `edit_file` | 唯一匹配原文替换 |
| `search_file` | Glob 按路径模式搜文件 |
| `search_content` | Grep 按内容搜代码 |
| `execute_command` | 执行 Shell（需 `command_execution_enabled`） |
| `install_skill` | 从 GitHub URL 安装 Skill |
| `Agent` | 启动子 Agent / Team 队员 |
| `TaskList` / `TaskGet` / `TaskStop` | 后台任务管理 |
| `SendMessage` | 向后台任务续派消息 |
| `TeamCreate` / `TeamDelete` / `TeamSendMessage` / `TeamTask*` | Agent Team（运行时注册） |

MCP 工具命名：`mcp__<server>__<tool>`。

权限规则中使用友好名：`Bash`、`Read`、`Write`、`Edit`、`Glob`、`Grep`（对应上述本地工具）。

---

## 高级功能用法

### 1. 权限系统

五层：黑名单 → 沙箱 → 规则 → 模式兜底 → HITL（人工确认）。

**模式：** `default` / `acceptEdits` / `plan` / `bypassPermissions`（子 Agent 另有 `dontAsk`）

**配置文件（优先级：local > project > user）：**

- `.lavendercode/permissions.local.yaml`
- `.lavendercode/permissions.yaml`
- `~/.lavendercode/permissions.yaml`

```yaml
defaultMode: default   # acceptEdits | plan | bypassPermissions | dontAsk
rules:
  - "Bash(git *)": allow
  - "Bash(=rm -rf /)": deny
  - "Write(*.env)": deny
```

模式前缀：`=` exact、`~` regex、`!` not，默认 glob。HITL 可选择允许本次 / 永久允许 / 拒绝（永久写入 `permissions.local.yaml`）。

### 2. Skills

放置目录（项目覆盖用户）：

- `~/.lavendercode/skills/<name>/`
- `.lavendercode/skills/<name>/`

格式：`SKILL.md`（YAML frontmatter）或 `skill.yaml` + `prompt.md`。

常用元数据：`name`、`description`、`whenToUse`、`allowed_tools`、`mode`（`inline`|`fork`）、`fork_context`（`none`|`recent`|`full`）。

调用：`/<name> [args]`，正文可用 `$ARGUMENTS`。也可让 Agent 调用 `install_skill` 从 GitHub 安装。

### 3. MCP

配置路径（注意大小写 `.LavenderCode`）：

- `~/.LavenderCode/config.yaml`
- `<项目>/.LavenderCode.yaml`

```yaml
mcp_servers:
  github:
    type: stdio
    command: npx
    args: ["-y", "@modelcontextprotocol/server-github"]
    env:
      GITHUB_TOKEN: ${GITHUB_TOKEN}
  remote:
    type: http
    url: https://example.com/mcp
    headers:
      Authorization: Bearer ${TOKEN}
```

启动时自动连接；失败仅告警，不阻断主程序。

### 4. 记忆与项目指令

| 路径 | 用途 |
|------|------|
| `LAVENDERCODE.md` | 项目指令（支持 `@include`） |
| `.lavendercode/LAVENDERCODE.md` | 项目级指令 |
| `~/.lavendercode/LAVENDERCODE.md` | 用户级指令 |
| `.lavendercode/memory/`、`~/.lavendercode/memory/` | 长期记忆（`MEMORY.md` 索引 + 笔记） |
| `.lavendercode/sessions/` | 会话 JSONL 存档 |

约每 5 轮或用户说「记住」等会触发记忆更新。用 `/memory` 查看，`/resume` 恢复历史会话（约 30 天清理）。

### 5. Hooks

配置：`~/.lavendercode/hooks.yaml` 或 `.lavendercode/hooks.yaml`。

事件示例：`SessionStart`、`PreToolUse`、`UserPromptSubmit`、`PreCompact` 等。动作：`shell` | `prompt` | `http` | `subagent`。用 `/hooks` 查看已加载规则；`PreToolUse` / `UserPromptSubmit` 可拦截（shell exit 2，或 HTTP 返回 `{"decision":"block"}`）。

### 6. 子 Agent

Agent 通过 `Agent` 工具委派。内置角色：`explore`、`plan`、`general-purpose`（见 `src/main/resources/subagent/builtin/`）。

自定义角色目录：

- `~/.lavendercode/agents/`
- `.lavendercode/agents/`

（Markdown + frontmatter）

后台任务：`TaskList` / `TaskGet` / `TaskStop` / `SendMessage`；前台超时或 Esc 可转后台。

### 7. Agent Team

- **Agent 侧：** `TeamCreate` → `Agent`（带 `teamName`）派队员 → `TeamTask*` / `TeamSendMessage`
- **用户侧：**

```text
/team list
/team info <name>
/team delete <name> [--force]
/team kill <member>
```

后端支持 tmux / iTerm2 / in-process。Coordinator 模式见上文双锁说明。

### 8. Worktree 隔离

需在 git 仓库根目录下使用：

```text
/worktree create <slug>
/worktree list
/worktree enter <slug>
/worktree exit [--remove] [--discard]
/worktree remove <slug> [--discard]
```

目录：`.lavendercode/worktrees/`。角色可声明 `isolation: worktree` 自动隔离。有未提交变更时删除需 `--discard`。

### 9. 上下文压缩

- 大工具结果落盘预览
- 逼近上下文窗口时自动九段摘要
- `/compact` 手动压缩
- `prompt_too_long` 时紧急压缩重试
- 由 `context_window` 驱动触发频率

---

## 旁路配置与数据目录

| 路径 | 用途 |
|------|------|
| `config.yaml` | 主配置（Provider / Options） |
| `~/.LavenderCode/`、`.LavenderCode.yaml` | MCP（注意大写） |
| `~/.lavendercode/permissions*.yaml`、`.lavendercode/permissions*.yaml` | 权限规则 |
| `~/.lavendercode/hooks.yaml`、`.lavendercode/hooks.yaml` | Hooks |
| `LAVENDERCODE.md` 及 `.lavendercode/` / `~/.lavendercode/` 下同名文件 | 项目/用户指令 |
| `~/.lavendercode/skills/`、`.lavendercode/skills/` | Skills |
| `~/.lavendercode/agents/`、`.lavendercode/agents/` | 子 Agent 角色 |
| `~/.lavendercode/memory/`、`.lavendercode/memory/` | 长期记忆 |
| `.lavendercode/sessions/` | 会话存档 |
| `.lavendercode/worktrees/` | Worktree 目录 |

---

## 项目结构

```
Lavendercode/
├── pom.xml
├── config.yaml.example
├── README.md
├── src/main/java/com/lavendercode/
│   ├── LavenderCode.java              # 入口
│   ├── chat/session/                  # 会话持久化
│   ├── chat/terminal/                 # TUI / ReAct / 斜杠命令
│   └── core/
│       ├── anthropic|openai|provider|sse/
│       ├── tool|command|config|prompt/
│       ├── permission|context|hook|skill|mcp|memory/
│       ├── subagent|task|team|worktree|coordinator/
├── src/main/resources/subagent/builtin/
├── docs/current/modules/              # 各模块 PRD / 设计文档
└── docs/superpowers/{specs,plans}/    # 实现规格与计划
```

---

## 技术栈

| 项 | 值 |
|----|-----|
| 语言 / 构建 | Java 21、Maven |
| 主类 | `com.lavendercode.LavenderCode` |
| HTTP | OkHttp 4.12 |
| 配置 | Jackson YAML + Bean Validation |
| 终端 | JLine 3.26 |
| Markdown | flexmark |
| MCP | Model Context Protocol Java SDK |

---

## 更多文档

- 模块需求与设计：`docs/current/modules/<模块名>/`
- 实现规格与计划：`docs/superpowers/specs/`、`docs/superpowers/plans/`

---

> *Write code like poetry, and let the terminal bloom lavender.*
