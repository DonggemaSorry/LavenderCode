# Agent Team（刀 1：in-process）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 LavenderCode 落地 Agent Team 网状协作的 **刀 1**：长期小组、邮箱、共享任务、同进程队员、协作工具、Plan 审批、Coordinator、Lead 收信与 `/team`，可在无 tmux 环境下验收。

**Architecture:** 新增 `com.lavendercode.core.team`（TeamManager / Mailbox / SharedTask / Backend 端口 / InProcessBackend）与 `com.lavendercode.core.coordinator.Coordinator`；协作工具 `Team*` 与 ch13 后台 `Task*`/`SendMessage` 并存；`Agent.teamName` 走 Team spawn；刀 1 仅实现同进程后端，检出窗格后端且未实现时失败（可用 `LAVENDERCODE_TEAM_BACKEND=in-process` 强制）。

**Tech Stack:** Java 21、Jackson、JUnit 5.10.3 + AssertJ、Maven、既有 TaskManager / WorktreeManager / Session jsonl

**Spec / Design 真源：**
- [2026-07-15-agent-team-design.md](../specs/2026-07-15-agent-team-design.md)
- [2026-07-15-agent-team-spec.md](../specs/2026-07-15-agent-team-spec.md)

**范围声明：** 本计划 **只含刀 1**。刀 2（tmux / iTerm2 / TeamMemberRunner）另开计划，勿塞进本文件。

**设计拍板（实现时勿再摇摆）：**

| 议题 | 决定 |
|:---|:---|
| 协作工具名 | `TeamTaskCreate/Get/List/Update`、`TeamSendMessage` |
| 路径 | `~/.lavendercode/teams/<sanitized>/` |
| Coordinator env | `LAVENDERCODE_COORDINATOR_MODE` |
| 强制同进程 | `LAVENDERCODE_TEAM_BACKEND=in-process` |
| Options 开关 | 在 `Options` record 增加 `coordinatorMode`、`forkTeammate`（Boolean，默认 false） |
| 依赖方向 | `core.team` 不 import `chat.terminal` |
| Maven 命令 | Windows PowerShell 下用 `mvn test "-Dtest=ClassName"` |

---

## 文件结构

### 新建（刀 1）

| 文件 | 职责 |
|:---|:---|
| `src/main/java/com/lavendercode/core/team/TeamName.java` | sanitize + 唯一后缀 |
| `src/main/java/com/lavendercode/core/team/TeammateInfo.java` | 成员 record |
| `src/main/java/com/lavendercode/core/team/Team.java` | 小组状态 + 原子写 config + reload-before-modify |
| `src/main/java/com/lavendercode/core/team/TeamManager.java` | 多 Team 管理、扫描、create/delete/spawnTeammate |
| `src/main/java/com/lavendercode/core/team/MailMessage.java` | 邮箱消息 record |
| `src/main/java/com/lavendercode/core/team/Mailbox.java` | 文件锁邮箱 |
| `src/main/java/com/lavendercode/core/team/SharedTask.java` | 共享任务 record |
| `src/main/java/com/lavendercode/core/team/SharedTaskStore.java` | tasks.json |
| `src/main/java/com/lavendercode/core/team/BackendType.java` | 枚举 |
| `src/main/java/com/lavendercode/core/team/Backend.java` | 端口接口 |
| `src/main/java/com/lavendercode/core/team/SpawnRequest.java` / `SpawnResult.java` | spawn 入参出参 |
| `src/main/java/com/lavendercode/core/team/BackendFactory.java` | detect + 创建实现 |
| `src/main/java/com/lavendercode/core/team/InProcessBackend.java` | 刀 1 实现 |
| `src/main/java/com/lavendercode/core/team/TeamExceptions.java`（或分散类） | NotFound / HasActive / BackendNotAvailable / InProcessNoSpawn |
| `src/main/java/com/lavendercode/core/task/AgentNameRegistry.java` | name↔id |
| `src/main/java/com/lavendercode/core/coordinator/Coordinator.java` | 双锁 + 白名单 + 提示词 |
| `src/main/java/com/lavendercode/core/tool/TeamCreateTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamDeleteTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamTaskCreateTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamTaskGetTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamTaskListTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamTaskUpdateTool.java` | |
| `src/main/java/com/lavendercode/core/tool/TeamSendMessageTool.java` | |
| `src/main/java/com/lavendercode/chat/terminal/LeadMailWatcher.java` | 轮询 Lead 邮箱 |
| `src/main/java/com/lavendercode/chat/terminal/LeadMailWaiter.java` | IDLE 自动续推 |
| 对应 `src/test/java/...` | 见各 Task |

### 修改（刀 1）

| 文件 | 修改 |
|:---|:---|
| `Options.java` | +`coordinatorMode` +`forkTeammate`；更新 compact/`withSystemPrompt`/默认构造；**全仓** `new Options(` 调用处补参 |
| `AgentTool.java` | 参数 `teamName`；Team spawn 分支 |
| `SubAgentServices.java` | 注入 `TeamManager` / `AgentNameRegistry`（可选） |
| `ToolFilter.java` 或 spawn 路径 | 队员注入协作五件套；主列表不含 |
| `NetworkOrchestrator.java` | 注册 Team* 工具；构造 TeamManager；Coordinator 收窄；启动 LeadMailWatcher |
| `BuiltinCommandRegistrar.java` | `/team` |
| `CommandContext.java` / `Impl` | 暴露 `TeamManager`（若 handler 需要） |
| `LavenderCode.java` | 若需在入口构造 TeamManager 并传入 Orchestrator |
| `ReActLoop.java` 或队员 runner 路径 | LLM 前读 mailbox 注入 `<incoming-messages>` |
| `AgentDefinition.java` / Parser | 可选字段 `planModeRequired`（或仅 spawn 参数） |

---

## Phase 1：命名与异常

### Task 1: TeamName.sanitize

**Files:**
- Create: `src/main/java/com/lavendercode/core/team/TeamName.java`
- Test: `src/test/java/com/lavendercode/core/team/TeamNameTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package com.lavendercode.core.team;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TeamNameTest {
    @Test
    void sanitizesSpacesAndSymbols() {
        assertThat(TeamName.sanitize("refactor auth")).isEqualTo("refactor-auth");
        assertThat(TeamName.sanitize("  a@b  ")).isEqualTo("a-b");
    }

    @Test
    void rejectsEmptyAfterSanitize() {
        assertThatThrownBy(() -> TeamName.sanitize("@@@"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("空");
    }

    @Test
    void uniqueSuffixAvoidsCollision() {
        assertThat(TeamName.ensureUnique("demo", java.util.Set.of("demo")))
            .isEqualTo("demo-2");
        assertThat(TeamName.ensureUnique("demo", java.util.Set.of("demo", "demo-2")))
            .isEqualTo("demo-3");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test "-Dtest=TeamNameTest" -q`  
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现**

```java
package com.lavendercode.core.team;

import java.util.Set;

public final class TeamName {
    private TeamName() {}

    public static String sanitize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("团队名不能为空");
        }
        String s = raw.trim().replaceAll("[^a-zA-Z0-9._-]+", "-")
            .replaceAll("^-+", "").replaceAll("-+$", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("团队名净化后为空");
        }
        return s;
    }

    public static String ensureUnique(String sanitized, Set<String> existing) {
        if (!existing.contains(sanitized)) {
            return sanitized;
        }
        for (int i = 2; i < 10_000; i++) {
            String cand = sanitized + "-" + i;
            if (!existing.contains(cand)) {
                return cand;
            }
        }
        throw new IllegalStateException("无法生成唯一团队名: " + sanitized);
    }
}
```

- [ ] **Step 4: 测试通过**

Run: `mvn test "-Dtest=TeamNameTest" -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/core/team/TeamName.java \
        src/test/java/com/lavendercode/core/team/TeamNameTest.java
git commit -m "feat(team): 新增 TeamName sanitize 与唯一后缀"
```

---

### Task 2: 异常类型

**Files:**
- Create: `src/main/java/com/lavendercode/core/team/TeamNotFoundException.java`
- Create: `src/main/java/com/lavendercode/core/team/TeamHasActiveMembersException.java`
- Create: `src/main/java/com/lavendercode/core/team/BackendNotAvailableException.java`
- Create: `src/main/java/com/lavendercode/core/team/InProcessTeammateNoSpawnException.java`

- [ ] **Step 1: 实现（无单独测试）**

```java
package com.lavendercode.core.team;

public final class TeamNotFoundException extends RuntimeException {
    public TeamNotFoundException(String name) {
        super("团队不存在: " + name);
    }
}

// TeamHasActiveMembersException.java
package com.lavendercode.core.team;
public final class TeamHasActiveMembersException extends RuntimeException {
    public TeamHasActiveMembersException(String name) {
        super("团队仍有活跃成员，无法删除: " + name);
    }
}

// BackendNotAvailableException.java
package com.lavendercode.core.team;
public final class BackendNotAvailableException extends RuntimeException {
    public BackendNotAvailableException(String msg) { super(msg); }
}

// InProcessTeammateNoSpawnException.java
package com.lavendercode.core.team;
public final class InProcessTeammateNoSpawnException extends RuntimeException {
    public InProcessTeammateNoSpawnException() {
        super("同进程队员不能再向 Team 派生成员");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/lavendercode/core/team/*Exception.java
git commit -m "feat(team): 新增 Team 领域异常类型"
```

---

## Phase 2：Team 持久化与 Manager

### Task 3: TeammateInfo + Team 原子写 / reload

**Files:**
- Create: `TeammateInfo.java`, `Team.java`
- Test: `src/test/java/com/lavendercode/core/team/TeamPersistenceTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package com.lavendercode.core.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TeamPersistenceTest {
    @TempDir Path tmp;

    @Test
    void addMemberPersistsAtomicallyAndReloadsFromDisk() throws Exception {
        Path configDir = tmp.resolve("demo");
        Files.createDirectories(configDir);
        Team team = new Team(
            "demo", "demo", "lead", BackendType.IN_PROCESS,
            "", Instant.now(), configDir, configDir.resolve("config.json"),
            new ArrayList<>(List.of(TeammateInfo.leadPlaceholder())));
        team.addMember(new TeammateInfo(
            "alice", "agent-1", "general-purpose", "",
            tmp.resolve("wt"), "worktree-team-demo+alice",
            BackendType.IN_PROCESS, "", true, false, tmp.resolve("sess")));
        assertThat(Files.exists(team.configPath())).isTrue();
        String json = Files.readString(team.configPath());
        assertThat(json).contains("alice").contains("agent-1");

        // 模拟另一进程先改盘：标 alice 空闲
        Team onDisk = Team.load(team.configPath());
        onDisk.setMemberActive("alice", false);

        // 内存中旧对象 add 不应丢掉 on-disk 的 isActive=false
        team.reloadFromDiskLocked();
        assertThat(team.findMember("alice").orElseThrow().isActive()).isFalse();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test "-Dtest=TeamPersistenceTest" -q`  
Expected: FAIL

- [ ] **Step 3: 实现最小 `TeammateInfo` / `Team` / `BackendType`**

`BackendType`：

```java
package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BackendType {
    TMUX("tmux"), ITERM2("iterm2"), IN_PROCESS("in-process");
    private final String wire;
    BackendType(String wire) { this.wire = wire; }
    @JsonValue public String wireValue() { return wire; }
    public static BackendType fromWire(String w) {
        for (BackendType t : values()) if (t.wire.equals(w)) return t;
        throw new IllegalArgumentException("未知 backend: " + w);
    }
}
```

`TeammateInfo`（字段对齐 Spec F2；Lead 占位 `isActive=null` 用 `Boolean`）：

```java
package com.lavendercode.core.team;

import java.nio.file.Path;

public record TeammateInfo(
    String name,
    String agentId,
    String agentType,
    String model,
    Path worktreePath,
    String branch,
    BackendType backendType,
    String paneId,
    Boolean isActive,
    boolean planModeRequired,
    Path sessionDir
) {
    public static TeammateInfo leadPlaceholder() {
        return new TeammateInfo("lead", "lead", "", "", null, "",
            BackendType.IN_PROCESS, "", null, false, null);
    }
}
```

`Team` 要点（实现时写全）：
- 字段：`name, sanitizedName, leadAgentId, backend, description, createdAt, configDir, configPath, members, lock`
- `addMember` / `setMemberActive` / `removeMember`：`synchronized(lock)` → `reloadFromDiskLocked()` → 改 list → `saveAtomic()`
- `saveAtomic`：写 `config.json.tmp` → `Files.move(..., ATOMIC_MOVE)`（Windows 上若 ATOMIC_MOVE 失败则 REPLACE_EXISTING 回退，单测用 TempDir）
- `load(Path)` / `reloadFromDiskLocked`：Jackson `ObjectMapper` + JavaTimeModule；枚举用 `wireValue`
- Jackson 对 `Path`：存绝对路径字符串

若缺 `jackson-datatype-jsr310`，在 `pom.xml` 增加与 databind 同版本依赖。

- [ ] **Step 4: 测试通过**

Run: `mvn test "-Dtest=TeamPersistenceTest" -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/core/team/ \
        src/test/java/com/lavendercode/core/team/TeamPersistenceTest.java pom.xml
git commit -m "feat(team): Team/TeammateInfo 持久化与 reload-before-modify"
```

---

### Task 4: TeamManager create / get / delete

**Files:**
- Create: `src/main/java/com/lavendercode/core/team/TeamManager.java`
- Test: `src/test/java/com/lavendercode/core/team/TeamManagerTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package com.lavendercode.core.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class TeamManagerTest {
    @TempDir Path home;

    private TeamManager mgr() {
        return new TeamManager(home, null, null, new com.lavendercode.core.task.AgentNameRegistry());
    }

    @Test
    void createWritesConfigUnderLavendercodeTeams() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        Team t = mgr().create("refactor auth", "");
        assertThat(t.sanitizedName()).isEqualTo("refactor-auth");
        Path cfg = home.resolve(".lavendercode/teams/refactor-auth/config.json");
        assertThat(Files.exists(cfg)).isTrue();
        assertThat(t.backend()).isEqualTo(BackendType.IN_PROCESS);
    }

    @Test
    void duplicateNameGetsSuffix() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        m.create("demo", "");
        Team t2 = m.create("demo", "");
        assertThat(t2.sanitizedName()).isEqualTo("demo-2");
    }

    @Test
    void deleteWithoutForceRejectsActiveMember() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        Team t = m.create("x", "");
        t.addMember(new TeammateInfo("a", "id1", "", "", home, "b",
            BackendType.IN_PROCESS, "", true, false, home));
        assertThatThrownBy(() -> m.delete("x", false))
            .isInstanceOf(TeamHasActiveMembersException.class);
        assertThat(Files.exists(t.configDir())).isTrue();
    }

    @Test
    void forceDeleteRemovesConfigDir() {
        System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
        TeamManager m = mgr();
        Team t = m.create("y", "");
        m.delete("y", true);
        assertThat(Files.exists(t.configDir())).isFalse();
    }
}
```

> 测后建议在 `@AfterEach` `System.clearProperty("LAVENDERCODE_TEAM_BACKEND")`。

- [ ] **Step 2: 运行确认失败**

Run: `mvn test "-Dtest=TeamManagerTest" -q`  
Expected: FAIL

- [ ] **Step 3: 实现 `TeamManager`（构造与 create/get/delete）**

```java
// 构造签名（对齐 Design）
public TeamManager(Path homeDir,
                   com.lavendercode.core.worktree.WorktreeManager worktreeManager,
                   com.lavendercode.core.task.TaskManager taskManager,
                   com.lavendercode.core.task.AgentNameRegistry registry)

// teamsRoot = homeDir.resolve(".lavendercode/teams")
// create: sanitize → ensureUnique → mkdir → Team.new → detectBackend → save → map.put
// delete: 见 Spec F7/F66；刀1 force 时 worktree/session 清理若 manager 为 null 则跳过并警告
// 启动扫描：list 子目录读 config.json，失败 stderr 警告
```

`BackendFactory.detect()` 伪代码：

```java
public static BackendType detect() {
    String force = System.getenv("LAVENDERCODE_TEAM_BACKEND");
    if (force == null) force = System.getProperty("LAVENDERCODE_TEAM_BACKEND");
    if (force != null && force.equalsIgnoreCase("in-process")) {
        return BackendType.IN_PROCESS;
    }
    if (System.getenv("TMUX") != null) return BackendType.TMUX;
    if ("iTerm.app".equals(System.getenv("TERM_PROGRAM")) && onPath("it2")) {
        return BackendType.ITERM2;
    }
    if (onPath("tmux")) return BackendType.TMUX;
    return BackendType.IN_PROCESS;
}
```

刀 1 `createBackend(type)`：非 `IN_PROCESS` → 抛 `BackendNotAvailableException`（强制 in-process 环境变量已使 create 走 IN_PROCESS）。

- [ ] **Step 4: 测试通过**

Run: `mvn test "-Dtest=TeamManagerTest" -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lavendercode/core/team/TeamManager.java \
        src/main/java/com/lavendercode/core/team/BackendFactory.java \
        src/main/java/com/lavendercode/core/team/BackendType.java \
        src/test/java/com/lavendercode/core/team/TeamManagerTest.java
git commit -m "feat(team): TeamManager create/get/delete 与 backend detect"
```

---

## Phase 3：Mailbox、SharedTask、Registry

### Task 5: Mailbox 并发安全

**Files:**
- Create: `MailMessage.java`, `Mailbox.java`
- Test: `src/test/java/com/lavendercode/core/team/MailboxTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package com.lavendercode.core.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.*;

class MailboxTest {
    @TempDir Path dir;

    @Test
    void writeAppendsUnreadWithTimestamp() {
        Mailbox box = new Mailbox(dir);
        box.write("agent-a", MailMessage.text("lead", "agent-a", "hi", "hello"));
        List<MailMessage> unread = box.readUnread("agent-a");
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).read()).isFalse();
        assertThat(unread.get(0).timestamp()).isPositive();
        box.markRead("agent-a", List.of(0));
        assertThat(box.readUnread("agent-a")).isEmpty();
    }

    @Test
    void concurrentWritesAllPersist() throws Exception {
        Mailbox box = new Mailbox(dir);
        int n = 10;
        var pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                box.write("a", MailMessage.text("lead", "a", "s" + idx, "c" + idx));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        // 读全量（含已读）
        assertThat(box.readAll("a")).hasSize(n);
    }
}
```

- [ ] **Step 2: 运行确认失败** → Step 3: 实现

`Mailbox` 要点：
- 路径：`root/mailbox/<agentId>.json`，锁：`root/mailbox/<agentId>.lock`
- 锁：`Files.newOutputStream(lock, CREATE_NEW)`；失败则 sleep 5–100ms 随机，最多 10 次；若 lock 存在且 `lastModifiedTime` > 10s → delete 再试
- RMW：读 JSON → append → 写 tmp → `ATOMIC_MOVE`
- `MailMessage.text(...)` 自动 `timestamp=now`, `read=false`, `type=text`

- [ ] **Step 4: PASS** → Step 5: Commit `feat(team): Mailbox 文件锁与并发写入`

---

### Task 6: SharedTaskStore

**Files:**
- Create: `SharedTask.java`, `SharedTaskStore.java`
- Test: `SharedTaskStoreTest.java`

- [ ] **Step 1: 失败测试**

```java
@Test
void createAndAddBlockedByUpdatesBidirectional() {
    SharedTaskStore store = new SharedTaskStore(teamConfigDir);
    String a = store.create("A", "", null, List.of());
    String b = store.create("B", "", null, List.of());
    store.update(b, null, null, null, null, List.of(), List.of(a), List.of(), List.of());
    SharedTask taskB = store.get(b).orElseThrow();
    SharedTask taskA = store.get(a).orElseThrow();
    assertThat(taskB.blockedBy()).contains(a);
    assertThat(taskA.blocks()).contains(b);
}

@Test
void isReadyFalseWhenBlockerPending() {
    // A blocks B; A pending → B isReady false；A completed → B isReady true
}
```

- [ ] **Step 2–5:** TDD 实现；`task_<6 hex>`；锁 `tasks.lock` 同 Mailbox；Commit `feat(team): SharedTaskStore 与依赖字段`

---

### Task 7: AgentNameRegistry

**Files:**
- Create: `src/main/java/com/lavendercode/core/task/AgentNameRegistry.java`
- Test: `src/test/java/com/lavendercode/core/task/AgentNameRegistryTest.java`

- [ ] **Step 1: 测试**

```java
@Test
void laterRegisterOverrides() {
    AgentNameRegistry r = new AgentNameRegistry();
    r.register("alice", "id-1");
    r.register("alice", "id-2");
    assertThat(r.resolve("alice")).contains("id-2");
    assertThat(r.nameOf("id-2")).contains("alice");
    assertThat(r.resolve("id-2")).contains("id-2"); // id 直查
}
```

- [ ] **Step 3: 实现** `ReentrantLock` + `byName` + `byId`；覆盖时清理旧 id 反查

- [ ] **Step 5: Commit** `feat(task): AgentNameRegistry name↔id`

---

## Phase 4：Backend 端口与 InProcess

### Task 8: Backend 接口 + InProcessBackend

**Files:**
- Create: `Backend.java`, `SpawnRequest.java`, `SpawnResult.java`, `InProcessBackend.java`
- Test: `InProcessBackendTest.java`（可用假 TaskManager / 计数 Callable）

- [ ] **Step 1: 接口**

```java
package com.lavendercode.core.team;

import java.io.IOException;

public interface Backend {
    BackendType type();
    SpawnResult spawn(SpawnRequest req) throws IOException;
    void wake(String paneId, String agentId) throws IOException;
    void kill(String paneId, String agentId) throws IOException;
}

public record SpawnResult(String paneId, String agentId) {}

public record SpawnRequest(
    String teamName,
    String memberName,
    String agentId,
    java.nio.file.Path worktreePath,
    java.nio.file.Path sessionDir,
    String agentType,
    String model,
    String initialPrompt,
    boolean planModeRequired,
    java.util.concurrent.Callable<String> work // in-process 由 TeamManager 填好
) {}
```

- [ ] **Step 3: `InProcessBackend`**

```java
public final class InProcessBackend implements Backend {
    private final com.lavendercode.core.task.TaskManager taskManager;
    public BackendType type() { return BackendType.IN_PROCESS; }
    public SpawnResult spawn(SpawnRequest req) {
        String id = taskManager.launch(req.work(), req.memberName());
        return new SpawnResult("", id);
    }
    public void wake(String paneId, String agentId) { /* no-op */ }
    public void kill(String paneId, String agentId) { taskManager.stop(agentId); }
}
```

> 注意：现网 `launch` 自己生成 id；若需 Spec 的预生成 `agentId`，本刀 1 采用 **launch 返回的 id 写回 TeammateInfo**（在 `spawnTeammate` 里用返回值），不强制预分配。

- [ ] **Step 5: Commit** `feat(team): Backend 端口与 InProcessBackend`

---

## Phase 5：协作工具

### Task 9: TeamCreateTool / TeamDeleteTool

**Files:**
- Create: `TeamCreateTool.java`, `TeamDeleteTool.java`
- Test: `TeamCreateDeleteToolTest.java`
- Modify: 稍后在 Task 16 注册到 Orchestrator；本 Task 单测直接 `new Tool(...).execute`

- [ ] **Step 1: 测试**

```java
@Test
void teamCreateReturnsJson() {
    System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
    TeamManager m = new TeamManager(home, null, null, new AgentNameRegistry());
    var tool = new TeamCreateTool(m);
    ToolResult r = tool.execute(Map.of("teamName", "demo"));
    assertThat(r.isError()).isFalse();
    assertThat(r.output()).contains("demo").contains("in-process");
}
```

- [ ] **Step 3: 实现**  
`name()` = `"TeamCreate"` / `"TeamDelete"`；参数 schema 对齐 Spec F20/F22。

- [ ] **Step 5: Commit** `feat(team): TeamCreate/TeamDelete 工具`

---

### Task 10: TeamTask* 工具

**Files:** 四个 Tool + `TeamTaskToolsTest.java`

- [ ] **TDD：** Create 返回 `taskId`；Update `addBlockedBy`；List 带 `isReady`  
- [ ] Tools 构造注入 `TeamManager` + 当前 team 解析：从 `ToolContext` 或线程上下文 `TeammateContext` 取 `teamName`（刀 1 定义 `TeammateContext` ThreadLocal / record，在 spawn 时 set）

**最小 `TeammateContext`：**

```java
package com.lavendercode.core.team;

public record TeammateContext(String teamName, String memberName, String agentId, boolean isLead) {
    private static final ThreadLocal<TeammateContext> TL = new ThreadLocal<>();
    public static void set(TeammateContext c) { TL.set(c); }
    public static TeammateContext get() { return TL.get(); }
    public static void clear() { TL.remove(); }
}
```

Lead 调用 TeamTask*：刀 1 **允许 Lead 在 Coordinator 白名单下使用**——Lead 侧 `TeammateContext` 设为 `isLead=true` 且绑定当前活跃 Team（TeamCreate 后记 `TeamManager.activeTeam` 或工具参数 `teamName` 可选；**拍板：TeamTask\* 增加可选 `teamName`，缺省用上下文**）。

- [ ] **Commit** `feat(team): TeamTask CRUD 工具`

---

### Task 11: TeamSendMessageTool

**Files:**
- Create: `TeamSendMessageTool.java`
- Test: `TeamSendMessageToolTest.java`

- [ ] **测试覆盖：** 点对点追加未读；`to=*` 广播除自己；非 Lead 发 `plan_approval_response` 失败；目标 in-process 已终止时触发续派钩子（可注入 `ResumeHandler` 接口便于单测）

```java
public interface TeammateResumeHandler {
    void resumeIfStopped(Team team, TeammateInfo member, String message) throws Exception;
}
```

- [ ] **Commit** `feat(team): TeamSendMessage 与广播/协议校验`

---

## Phase 6：Spawn、空闲、续派、消息注入

### Task 12: TeamManager.spawnTeammate + AgentTool.teamName

**Files:**
- Modify: `AgentTool.java`（schema + 分支）
- Modify: `TeamManager.java`（`spawnTeammate`）
- Modify: `SubAgentServices.java`（持有 TeamManager）
- Test: `AgentToolTeamSpawnTest.java`

- [ ] **Step 1: 测试要点**

```java
@Test
void withoutTeamNameKeepsLegacyBehavior() { /* 现有单测仍绿 */ }

@Test
void withTeamNameCreatesMemberWorktreeAndRegisters() {
    // 可用 mock WorktreeManager / 假 Backend
    // 断言 members 含 alice、registry.resolve("alice") 有值
}

@Test
void inProcessTeammateCannotSpawnIntoTeam() {
    TeammateContext.set(new TeammateContext("t", "bob", "id", false));
    // execute Agent with teamName → InProcessTeammateNoSpawnException / ToolResult.error
}
```

- [ ] **Step 3: spawn 流程（实现清单）**

1. `TeamManager.get(teamName)`  
2. `worktreeManager.create("team-"+sanitized+"/"+memberName, "HEAD", false)`  
3. `PersistingSessionManager` 方式或 `SessionPaths`+`ensureDirectories` 建 `sessionDir`  
4. 构造 work Callable：设置 `TeammateContext`、注入协作工具到 runner、初始 PLAN 若需要、跑 `runToCompletion`、finally：`setMemberActive(false)` + Mailbox idle 给 lead  
5. `InProcessBackend.spawn`  
6. `registry.register` + `team.addMember`  

**工具注入：** 在 `SubAgentServices.createToolExecutor` 增加重载或 flag `teamCollaboration=true` 时，把五个 `Team*` 工具注册进该子 Agent 的 registry 视图（勿污染全局 `ToolRegistry` 主列表）。拍板：**协作工具仅挂在队员 Tool 列表，不 `ToolRegistry.register` 到全局**；Lead 的 `TeamCreate/Delete` 与 Coordinator 下的 `TeamTask*`/`TeamSendMessage` 在 `NetworkOrchestrator` 全局注册（主 Agent 可见 Create/Delete；五件套全局注册后靠 Coordinator 白名单与「非队员不在默认列表」——为简化刀 1：

**拍板（可见性）：**  
- 全局注册：`TeamCreate`, `TeamDelete`, `TeamTask*`, `TeamSendMessage`  
- 非 Coordinator 的主 Agent：`TeamCreate`/`TeamDelete` 可见；`TeamTask*`/`TeamSendMessage` 用 `ToolFilter` 或 Orchestrator `setAllowedTools` **默认从主 Agent 去掉五件套**，仅队员执行器与 Coordinator Lead 放开  

- [ ] **Commit** `feat(team): Agent.teamName spawn 与队员工具注入`

---

### Task 13: 未读消息注入 `<incoming-messages>`

**Files:**
- Modify: 队员 ReAct 循环入口（定位 `SubAgentLauncher` / runner 在每轮 LLM 前的 hook；若无 hook 则在 `SubAgentServices` 增加 `beforeLlmRound` 回调）
- Test: `IncomingMessagesInjectionTest.java`（单位：给定 unread → 生成 reminder 字符串 → markRead）

- [ ] **实现纯函数**

```java
public final class IncomingMessageFormatter {
    public static String format(List<MailMessage> unread) { /* Spec F42 */ }
}
```

接线：队员循环每轮调用 `Mailbox.readUnread` → append system reminder → `markRead`。

- [ ] **Commit** `feat(team): 队员未读消息 reminder 注入`

---

### Task 14: Plan 审批路径

**Files:**
- Test: `PlanApprovalFlowTest.java`
- Modify: `TeamSendMessageTool` / 队员消息处理

- [ ] **测试：**  
  - spawn `planModeRequired=true` → 初始模式 PLAN（断言 pipeline / context）  
  - 收到 `plan_approval_response approve=true` → 切换 DEFAULT  
  - reject → feedback 进入下一轮用户消息语义  

- [ ] **Commit** `feat(team): planModeRequired 与 plan_approval_response`

---

### Task 15: 空闲通知与 in-process 续派

**Files:**
- Modify: spawn finally + `TeamSendMessageTool` / `TeammateResumeHandler`
- Test: `TeammateResumeTest.java`

- [ ] **续派实现：**

```java
// TeammateResumeHandlerImpl
public void resumeIfStopped(Team team, TeammateInfo member, String message) throws Exception {
    BackgroundTask t = taskManager.get(member.agentId());
    if (t != null && !t.isTerminated()) return;
    List<Message> hist = SessionRestorer.parseMessages(
        member.sessionDir().resolve("conversation.jsonl"));
    // launch 新 work：恢复 hist + initial message；setMemberActive(true)
}
```

- [ ] **Commit** `feat(team): 队员 idle 通知与 session 续派`

---

## Phase 7：Coordinator、Lead 收信、Slash、接线

### Task 16: Coordinator + Options 开关

**Files:**
- Create: `Coordinator.java`
- Modify: `Options.java`（+2 字段及所有调用点）
- Test: `CoordinatorTest.java`

- [ ] **Step 1: 测试**

```java
@Test
void requiresBothLocks() {
    Options off = new Options(/* ... coordinatorMode=false ... */);
    assertThat(Coordinator.isEnabled(off)).isFalse();
    // 设 env 难：抽取 EnvAccess 接口或用 Coordinator.isEnabled(options, envMap)
}

@Test
void allowedToolsExcludesWriteEdit() {
    assertThat(Coordinator.ALLOWED_TOOLS).contains("execute_command", "TeamSendMessage");
    assertThat(Coordinator.ALLOWED_TOOLS).doesNotContain("write_file", "edit_file");
}
```

- [ ] **Step 3:**

```java
public final class Coordinator {
    public static final List<String> ALLOWED_TOOLS = List.of(
        "Agent", "TeamCreate", "TeamDelete",
        "TeamTaskCreate", "TeamTaskGet", "TeamTaskList", "TeamTaskUpdate",
        "TeamSendMessage",
        "read_file", "search_file", "search_content", "execute_command");

    public static boolean isEnabled(Options opt, java.util.function.Function<String,String> env) {
        if (!Boolean.TRUE.equals(opt.coordinatorMode())) return false;
        return envTruthy(env.apply("LAVENDERCODE_COORDINATOR_MODE"));
    }
    // SYSTEM_PROMPT_SUFFIX 中文四阶段纪律（Design F55）
}
```

`Options` 在现有组件列表末尾追加 `Boolean coordinatorMode`, `Boolean forkTeammate`，compact 中 null→false；**用 IDE/ ripgrep 修复全部 `new Options(`**。

- [ ] **Commit** `feat(coordinator): 双锁启用与工具白名单`

---

### Task 17: LeadMailWatcher + `/team` + Orchestrator 接线

**Files:**
- Create: `LeadMailWatcher.java`, `LeadMailWaiter.java`
- Modify: `BuiltinCommandRegistrar.java`, `NetworkOrchestrator.java`, `LavenderCode.java`（若需要）
- Test: `TeamCommandTest.java`, `LeadMailWatcherTest.java`

- [ ] **`/team` handler 子命令：** `list` / `info` / `delete [--force]` / `kill <member>`  
  模式抄 `/worktree`：`def("team", List.of(), "...", LOCAL, BuiltinCommandRegistrar::handleTeam)`

- [ ] **LeadMailWatcher：** 每秒 `teamManager.pollLeadMailboxes()` → 构造 `<team-update>` → 注入 reminder API（对接现有 `pendingReminders` / `appendReminders`；先读 `ReminderInjector`/`NetworkOrchestrator` 现有入口再接线）  
  IDLE 自动续推：若现网无 `beginAutonomousTurn`，刀 1 **最低交付**=提醒注入；自动续推为增强——实现时优先找到与 `TaskNotificationInjector` 相似路径；若缺失则在计划备注「降级：仅 pendingReminders，手动再发一条即可」并实现 Watcher 单测。

- [ ] **Orchestrator：** 构造 `TeamManager(home, worktreeManager, taskManager, registry)`；注册工具；若 `Coordinator.isEnabled` → 限制 Lead allowed tools + 追加 system prompt；启动 Watcher 线程。

- [ ] **Commit** `feat(team): /team 命令、Lead 收信与启动接线`

---

## Phase 8：刀 1 端到端与回归

### Task 18: AC29 集成测试 + 全量回归

**Files:**
- Create: `src/test/java/com/lavendercode/core/team/AgentTeamInProcessIT.java`

- [ ] **Step 1: 集成测试骨架**

```java
@Test
void inProcessTeamCreateSpawnIdleResume() {
    System.setProperty("LAVENDERCODE_TEAM_BACKEND", "in-process");
    // 1. TeamManager.create("inproc")
    // 2. spawnTeammate 跑一个立即结束的 Callable（写标记文件）
    // 3. 断言 isActive=false 且 lead mailbox 含 idle
    // 4. TeamSendMessage 续派，断言再次运行（第二标记文件）
}
```

不依赖真实 LLM：spawn 的 `work` 用测试用 Callable，不走完整 Agent loop。完整 LLM 路径留手动 AC29。

- [ ] **Step 2: 全量**

Run: `mvn test -q`  
Expected: PASS  

Run: `mvn -q -DskipTests package`  
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/lavendercode/core/team/AgentTeamInProcessIT.java
git commit -m "test(team): 刀1 in-process 集成与回归绿灯"
```

---

## 刀 2 后续（不在本计划执行）

另开 `docs/superpowers/plans/2026-07-15-agent-team-pane-backends-implementation.md`：

- `TmuxBackend` / `Iterm2Backend`
- `TeamMemberRunner` + CLI（可加 picocli 依赖或手写 argv）
- detect 成功后真实 spawn；去掉「窗格未实现」错误
- AC23/24/28

---

## Spec 覆盖自检（刀 1）

| Spec / Design | 本计划 Task |
|:---|:---|
| G1–G4 Team 生命周期 | T1–T4, T9 |
| G5 Backend detect + in-process | T4, T8 |
| G6–G10 协作工具与邮箱 | T5–T6, T10–T11, T13 |
| G11–G13 spawn/idle/resume | T12, T15 |
| G14 Plan | T14 |
| G15 Coordinator | T16 |
| G16 收敛 | 无专用工具；靠 execute_command（文档说明即可） |
| G17 `/team` | T17 |
| G18 共存 | T12 可见性拍板 |
| AC14 并发邮箱 | T5 |
| AC29 | T18 |
| 刀2 AC23/28 | 不在本计划 |

## Placeholder 扫描

已避免 TBD/「适当处理」；Options 全仓补参、Reminder 接线需执行者读现网 API——已在 T17 标明对照点。

## 类型一致性

- 工具名全程 `Team*`  
- 环境变量 `LAVENDERCODE_*`  
- `BackendType.IN_PROCESS` / wire `in-process`  
- Lead id 固定 `"lead"`
