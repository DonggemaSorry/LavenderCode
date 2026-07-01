# LavenderCode 工具系统 实现计划

> **For agentic workers:** Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 LavenderCode 装上工具系统——模型能读文件、写文件、改文件、执行命令、按模式找文件、搜代码内容，形成单轮闭环（调用→执行→回灌→续答→停止）。

**Architecture:** 新增 `core.tool` 包；扩展 `StreamEvent`/`DeltaEvent`/`Message`/`Role`/`SessionManager`/`LlmProvider`/`NetworkOrchestrator`/`RenderEvent`/`MessageBlock`；新增 `AgentPromptBuilder`。严格 TDD：先写失败测试→验证失败→最小实现→验证通过→commit。

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito, Maven, OkHttp MockWebServer

**Specs:** `docs/current/modules/tool-system/` (PRD v1.0, TECH v1.1)

**Total tasks: 39** | **Estimated commits: 39**

---

## Phase 1: Foundation — Data Models (Tasks 1-7)

### Task 1: ToolParameterSchema (纯 record)

**Files:** Create `core/tool/ToolParameterSchema.java`, test at `core/tool/ToolParameterSchemaTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.lavendercode.core.tool;
import org.junit.jupiter.api.Test;
import java.util.List; import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ToolParameterSchemaTest {
    @Test
    void shouldCreateWithRequiredParams() {
        var s = new ToolParameterSchema("object",
            Map.of("path", new ToolParameterSchema.PropertyDef("string","路径",null,null)),
            List.of("path"));
        assertThat(s.type()).isEqualTo("object");
        assertThat(s.required()).containsExactly("path");
    }
    @Test
    void shouldSupportEnumAndItems() {
        var pd = new ToolParameterSchema.PropertyDef("string","颜色",List.of("r","g"),null);
        assertThat(pd.enumValues()).contains("r","g");
    }
    @Test
    void emptyRequiredIsAllowed() {
        var s = new ToolParameterSchema("object",Map.of(),List.of());
        assertThat(s.required()).isEmpty();
    }
}
```

- [ ] **Step 2: Verify test fails** — `mvn test -Dtest=ToolParameterSchemaTest` → compilation error
- [ ] **Step 3: Implement**

```java
package com.lavendercode.core.tool;
import java.util.List; import java.util.Map;
public record ToolParameterSchema(String type, Map<String, PropertyDef> properties, List<String> required) {
    public record PropertyDef(String type, String description, List<String> enumValues, PropertyDef items) {}
}
```

- [ ] **Step 4: Verify passes** — `mvn test -Dtest=ToolParameterSchemaTest` → 3/3 PASS
- [ ] **Step 5: Commit** — `git commit -m "feat: add ToolParameterSchema data model"`

---

### Task 2: TruncationInfo (纯 record)

**Files:** Create `core/tool/TruncationInfo.java`, test `TruncationInfoTest.java`

- [ ] **Step 1: Test**

```java
class TruncationInfoTest {
    @Test void allFields() { var t = new TruncationInfo(5000,2000,0,2000); assertThat(t.totalCount()).isEqualTo(5000); }
    @Test void partialDisplay() { var t = new TruncationInfo(150,100,50,200); assertThat(t.offset()).isEqualTo(50); }
}
```

- [ ] **Step 3: Implement**

```java
package com.lavendercode.core.tool;
/** 截断元信息，为未来分页能力预留协议空间。 */
public record TruncationInfo(int totalCount, int displayedCount, int offset, int limit) {}
```

- [ ] **Step 5: Commit** — `git commit -m "feat: add TruncationInfo data model"`

---

### Task 3: ToolResult (record + 工厂方法)

**Files:** Create `core/tool/ToolResult.java`, test `ToolResultTest.java`

- [ ] **Step 1: Test**

```java
class ToolResultTest {
    @Test void successResult() { var r=ToolResult.success("ok","content"); assertThat(r.success()).isTrue(); assertThat(r.summary()).isEqualTo("ok"); }
    @Test void successWithTruncation() { var r=ToolResult.success("ok","c",new TruncationInfo(5,2,0,2)); assertThat(r.truncationInfo()).isNotNull(); }
    @Test void errorResult() { var r=ToolResult.error("FILE_NOT_FOUND","文件不存在","detail"); assertThat(r.success()).isFalse(); assertThat(r.errorCategory()).isEqualTo("FILE_NOT_FOUND"); }
    @Test void timeoutError() { var r=ToolResult.error("TIMEOUT","超时","over 120s"); assertThat(r.errorCategory()).isEqualTo("TIMEOUT"); }
}
```

- [ ] **Step 3: Implement**

```java
package com.lavendercode.core.tool;
public record ToolResult(boolean success, String summary, String content, String errorCategory, String errorDetail, TruncationInfo truncationInfo) {
    public static ToolResult success(String summary, String content) { return new ToolResult(true,summary,content,null,null,null); }
    public static ToolResult success(String summary, String content, TruncationInfo t) { return new ToolResult(true,summary,content,null,null,t); }
    public static ToolResult error(String category, String summary, String detail) { return new ToolResult(false,summary,null,category,detail,null); }
}
```

- [ ] **Step 5: Commit** — `git commit -m "feat: add ToolResult model with factory methods"`

---

### Task 4: ToolCall (record)

**Files:** Create `core/tool/ToolCall.java`, test `ToolCallTest.java`

- [ ] **Step 1: Test**

```java
class ToolCallTest {
    @Test void basic() { var c=new ToolCall("id","read_file",Map.of("path","/x")); assertThat(c.parseError()).isNull(); }
    @Test void withParseError() { var c=new ToolCall("id","f",Map.of()).withParseError("bad json"); assertThat(c.parseError()).isEqualTo("bad json"); }
}
```

- [ ] **Step 3: Implement**

```java
package com.lavendercode.core.tool;
import java.util.Map;
public record ToolCall(String id, String name, Map<String,Object> parameters, String parseError) {
    public ToolCall(String id, String name, Map<String,Object> parameters) { this(id,name,parameters,null); }
    public ToolCall withParseError(String error) { return new ToolCall(id,name,parameters,error); }
}
```

- [ ] **Step 5: Commit** — `git commit -m "feat: add ToolCall data model"`

---

### Task 5: ToolDefinition (record)

**Files:** Create `core/tool/ToolDefinition.java`, test `ToolDefinitionTest.java`

- [ ] **Test + Implement**

```java
// ToolDefinition.java
package com.lavendercode.core.tool;
import java.util.Map;
public record ToolDefinition(String name, String description, Map<String,Object> parameters) {}
```

Test: create with name/desc/params map, verify all fields. Commit.

---

### Task 6: Tool Interface

**Files:** Create `core/tool/Tool.java`, test `ToolInterfaceTest.java`

- [ ] **Test + Implement**

```java
// Tool.java
package com.lavendercode.core.tool;
import java.util.Map;
public interface Tool {
    String name();
    String description();
    ToolParameterSchema parameters();
    ToolResult execute(Map<String, Object> params);
}
```

Test: anonymous Tool impl, verify name/desc/params/execute. Commit.

---

### Task 7: ToolTimeoutException

**Files:** Create `core/tool/ToolTimeoutException.java`, test

```java
package com.lavendercode.core.tool;
public class ToolTimeoutException extends RuntimeException {
    public ToolTimeoutException(String toolName, long timeoutSeconds) {
        super("Tool '" + toolName + "' timed out after " + timeoutSeconds + " seconds");
    }
}
```

Test: assert message contains toolName + timeoutSeconds. Commit.

---

## Phase 2: ToolRegistry & ToolCallAccumulator (Tasks 8-9)

### Task 8: ToolRegistry

**Files:** Create `core/tool/ToolRegistry.java`, test `ToolRegistryTest.java`

- [ ] **Step 1: Test** — register, get, unregister, has, size, clear, export, duplicate overwrite, empty export

```java
class ToolRegistryTest {
    @AfterEach void cleanup() { ToolRegistry.clear(); }

    @Test void registerAndGet() { ToolRegistry.register(dummy("t")); assertThat(ToolRegistry.get("t")).isNotNull(); assertThat(ToolRegistry.has("t")).isTrue(); }
    @Test void nullForUnknown() { assertThat(ToolRegistry.get("x")).isNull(); }
    @Test void overwriteOnDuplicate() { ToolRegistry.register(dummy("d")); Tool t2=dummy("d"); ToolRegistry.register(t2); assertThat(ToolRegistry.get("d")).isSameAs(t2); assertThat(ToolRegistry.size()).isEqualTo(1); }
    @Test void unregister() { ToolRegistry.register(dummy("u")); ToolRegistry.unregister("u"); assertThat(ToolRegistry.has("u")).isFalse(); }
    @Test void exportDefs() { ToolRegistry.register(dummy("a")); ToolRegistry.register(dummy("b")); assertThat(ToolRegistry.export()).hasSize(2).extracting(ToolDefinition::name).contains("a","b"); }
    @Test void clear() { ToolRegistry.register(dummy("x")); ToolRegistry.clear(); assertThat(ToolRegistry.size()).isZero(); }

    private Tool dummy(String name) { return new Tool() {
        @Override public String name() { return name; }
        @Override public String description() { return "d"; }
        @Override public ToolParameterSchema parameters() { return new ToolParameterSchema("object",Map.of(),List.of()); }
        @Override public ToolResult execute(Map<String,Object> p) { return ToolResult.success("ok",""); }
    };}
}
```

- [ ] **Step 3: Implement** — ConcurrentHashMap-based, register/get/unregister/has/size/clear/export with `toDefinition()` building JSON-Schema-compatible params map. (See TECH §4.5)

- [ ] **Step 5: Commit**

---

### Task 9: ToolCallAccumulator

**Files:** Create `core/tool/ToolCallAccumulator.java`, test `ToolCallAccumulatorTest.java`

- [ ] **Step 1: Test** — single frag, multi-tool interleaved, parse error, unknown id, clear, empty check

```java
class ToolCallAccumulatorTest {
    @Test void singleFragments() { var a=new ToolCallAccumulator(); a.start("id1","read"); a.append("id1","{\"p"); a.append("id1","\":\"v\"}"); var c=a.complete("id1"); assertThat(c.parameters()).containsEntry("p","v"); }
    @Test void interleaved() { var a=new ToolCallAccumulator(); a.start("c1","t1"); a.start("c2","t2"); a.append("c1","{\"x\":1}"); a.append("c2","{\"y\":2}"); var r2=a.complete("c2"); var r1=a.complete("c1"); assertThat(r2.parameters()).containsEntry("y",2); assertThat(r1.parameters()).containsEntry("x",1); }
    @Test void parseError() { var a=new ToolCallAccumulator(); a.start("b","t"); a.append("b","!!!"); var c=a.complete("b"); assertThat(c.parseError()).isNotNull(); }
    @Test void nullForUnknown() { assertThat(new ToolCallAccumulator().complete("x")).isNull(); }
    @Test void clear() { var a=new ToolCallAccumulator(); a.start("a","t"); a.clear(); assertThat(a.isEmpty()).isTrue(); }
}
```

- [ ] **Step 3: Implement** — LinkedHashMap<String,Accum>, Accum stores id+name+StringBuilder, Jackson ObjectMapper for JSON parse. (See TECH §4.2)

- [ ] **Step 5: Commit**

---

## Phase 3: Six Core Tools (Tasks 10-15)

### Task 10: ReadFileTool

**Files:** Create `core/tool/ReadFileTool.java`, test `ReadFileToolTest.java`

- [ ] **Step 1: Test** — read with line numbers, non-existent→FILE_NOT_FOUND, offset+limit, truncation, non-absolute→INVALID_PARAMETER, unreadable→FILE_NOT_READABLE

```java
class ReadFileToolTest {
    @TempDir Path dir;
    ReadFileTool tool = new ReadFileTool(2000);

    @Test void readsWithLineNumbers() throws Exception {
        Files.writeString(dir.resolve("f.txt"),"a\nb\nc");
        var r=tool.execute(Map.of("path",dir.resolve("f.txt").toString()));
        assertThat(r.success()).isTrue(); assertThat(r.content()).contains("1: a").contains("3: c");
    }
    @Test void fileNotFound() { var r=tool.execute(Map.of("path","/nope.txt")); assertThat(r.success()).isFalse(); assertThat(r.errorCategory()).isEqualTo("FILE_NOT_FOUND"); }
    @Test void offsetAndLimit() throws Exception {
        Path f=dir.resolve("l.txt"); var sb=new StringBuilder(); for(int i=1;i<=50;i++) sb.append("L").append(i).append("\n"); Files.writeString(f,sb.toString());
        var r=tool.execute(Map.of("path",f.toString(),"offset",10,"limit",5));
        assertThat(r.content()).contains("10: L10").doesNotContain("15: L15");
    }
    @Test void truncation() throws Exception {
        Path f=dir.resolve("b.txt"); var sb=new StringBuilder(); for(int i=1;i<=100;i++) sb.append("line\n"); Files.writeString(f,sb.toString());
        var rt=new ReadFileTool(10); var r=rt.execute(Map.of("path",f.toString()));
        assertThat(r.truncationInfo()).isNotNull(); assertThat(r.truncationInfo().totalCount()).isEqualTo(100);
    }
    @Test void nonAbsolutePath() { var r=tool.execute(Map.of("path","relative.txt")); assertThat(r.errorCategory()).isEqualTo("INVALID_PARAMETER"); }
}
```

- [ ] **Step 3: Implement**

```java
package com.lavendercode.core.tool;
import java.io.IOException; import java.nio.file.*; import java.util.*;

public class ReadFileTool implements Tool {
    private final int maxLines;
    public ReadFileTool(int maxLines) { this.maxLines = maxLines; }
    @Override public String name() { return "read_file"; }
    @Override public String description() { return "Reads a file from the local filesystem and returns its content with line numbers."; }
    @Override public ToolParameterSchema parameters() { return new ToolParameterSchema("object",
        Map.of("path",new PropertyDef("string","文件绝对路径",null,null),
               "offset",new PropertyDef("integer","起始行号(1-based)",null,null),
               "limit",new PropertyDef("integer","最大读取行数",null,null)),List.of("path")); }

    @Override public ToolResult execute(Map<String,Object> params) {
        String pathStr = (String) params.get("path");
        if (pathStr == null || !Path.of(pathStr).isAbsolute()) return ToolResult.error("INVALID_PARAMETER","路径无效",pathStr);
        int offset = params.containsKey("offset") ? ((Number)params.get("offset")).intValue() : 1;
        int limit = params.containsKey("limit") ? ((Number)params.get("limit")).intValue() : maxLines;
        Path path = Path.of(pathStr);
        if (!Files.exists(path)) return ToolResult.error("FILE_NOT_FOUND","文件不存在·"+pathStr,pathStr);
        if (!Files.isReadable(path)) return ToolResult.error("FILE_NOT_READABLE","文件不可读·"+pathStr,pathStr);
        try {
            List<String> allLines = Files.readAllLines(path);
            int total = allLines.size();
            int startIdx = Math.max(0, offset-1);
            int endIdx = Math.min(allLines.size(), startIdx+limit);
            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) sb.append(i+1).append(": ").append(allLines.get(i)).append("\n");
            String content = sb.toString();
            boolean truncated = endIdx < total;
            String summary = (endIdx-startIdx)+" 行·"+path.getFileName();
            if (truncated) return ToolResult.success(summary, content, new TruncationInfo(total, endIdx-startIdx, offset-1, limit));
            return ToolResult.success(summary, content);
        } catch (IOException e) { return ToolResult.error("TOOL_ERROR","读取失败",e.getMessage()); }
    }
}
```

- [ ] **Step 5: Commit**

---

### Task 11: WriteFileTool

**Files:** Create `core/tool/WriteFileTool.java`, test `WriteFileToolTest.java`

- [ ] **Test** — create new file, overwrite existing, auto-create parent dirs, non-absolute→INVALID_PARAMETER

- [ ] **Implement** — `name()="write_file"`, desc, params: path(string req)+content(string req). execute(): validate abs path, `Files.createDirectories(parent)`, `Files.writeString(path,content)`, return success with summary `"写入 {bytes} 字节·{filename}"`.

- [ ] **Commit**

---

### Task 12: EditFileTool

**Files:** Create `core/tool/EditFileTool.java`, test `EditFileToolTest.java`

- [ ] **Test** — unique match → replace success, zero match → NO_MATCH with old_string snippet, multiple matches (3)→ MULTIPLE_MATCHES with ≤5 positions (line+±1 context), file doesn't exist→FILE_NOT_FOUND

- [ ] **Implement** — `indexOf(old_string)` loop counting N. N==1: `replace(old,new)` + `Files.writeString()` → success. N==0: NO_MATCH error. N>1: collect positions, format `"第{line}行: ..."` with ±1 context lines, max 5. (See TECH §4.7.3)

- [ ] **Commit**

---

### Task 13: ExecuteCommandTool

**Files:** Create `core/tool/ExecuteCommandTool.java`, test `ExecuteCommandToolTest.java`

- [ ] **Test** — echo → stdout/exit 0, non-zero exit → COMMAND_FAILED with stderr, timeout → TIMEOUT, command disabled → COMMAND_DISABLED

- [ ] **Implement** — `name()="execute_command"`, params: command(string req)+working_dir(string opt). execute(): check `enabled` flag, ProcessBuilder with `directory()`, `waitFor(timeoutSeconds,SECONDS)`, read stdout/stderr with `inputStream()`/`errorStream()`, truncate output to maxChars. Timeout → `destroyForcibly()`.

- [ ] **Commit**

---

### Task 14: GlobTool

**Files:** Create `core/tool/GlobTool.java`, test `GlobToolTest.java`

- [ ] **Test** — match files by glob, no matches → empty list, respect maxResults truncation

- [ ] **Implement** — `name()="search_file"`, params: pattern(string req)+directory(string opt). execute(): `Files.walk(dir)`, `FileSystem.getPathMatcher("glob:"+pattern)`, filter hidden dirs, limit to maxResults, return paths list.

- [ ] **Commit**

---

### Task 15: GrepTool

**Files:** Create `core/tool/GrepTool.java`, test `GrepToolTest.java`

- [ ] **Test** — search content matches, case insensitive, file_pattern filter, truncation

- [ ] **Implement** — `name()="search_content"`, params: pattern+directory+file_pattern+case_sensitive. execute(): walk files matching file_pattern glob, line-by-line contains/match, skip binary, return `"{file}:{line}: {content}"`, maxResults truncation.

- [ ] **Commit**

---

## Phase 4: StreamEvent & DeltaEvent Extensions (Tasks 16-17)

### Task 16: StreamEvent — add tool call subtypes

**Files:** Modify `core/provider/StreamEvent.java`, test `StreamEventTest.java`

- [ ] **Step 1: Test** — verify new sealed subtypes compile and can be pattern-matched

```java
class StreamEventTest {
    @Test void toolCallStart() { var e=new StreamEvent.ToolCallStart("id","name"); assertThat(e.toolCallId()).isEqualTo("id"); assertThat(e.toolName()).isEqualTo("name"); }
    @Test void toolCallDelta() { var e=new StreamEvent.ToolCallDelta("id","{}"); assertThat(e.jsonFragment()).isEqualTo("{}"); }
    @Test void toolCallEnd() { var e=new StreamEvent.ToolCallEnd("id","name",Map.of("k","v")); assertThat(e.parameters()).containsEntry("k","v"); }
}
```

- [ ] **Step 3: Modify StreamEvent.java** — add three permits and records:

```java
public sealed interface StreamEvent
    permits StreamEvent.ContentDelta,
            StreamEvent.ThinkingDelta,
            StreamEvent.ToolCallStart,
            StreamEvent.ToolCallDelta,
            StreamEvent.ToolCallEnd,
            StreamEvent.StreamComplete,
            StreamEvent.StreamError {
    // ... existing records unchanged ...
    record ToolCallStart(String toolCallId, String toolName) implements StreamEvent {}
    record ToolCallDelta(String toolCallId, String jsonFragment) implements StreamEvent {}
    record ToolCallEnd(String toolCallId, String toolName, Map<String, Object> parameters) implements StreamEvent {}
}
```

- [ ] **Step 4: Run ALL existing tests** — `mvn test` must pass (new subtypes don't break existing exhaustive switch)

- [ ] **Step 5: Commit**

---

### Task 17: DeltaEvent — add tool call subtypes

**Files:** Modify `chat/terminal/DeltaEvent.java`, test `DeltaEventTest.java`

- [ ] **Step 1: Test** — ToolCallStart/ToolCallDelta/ToolCallEnd create

- [ ] **Step 3: Modify DeltaEvent.java** — add three permits and records:

```java
public sealed interface DeltaEvent
    permits DeltaEvent.Content,
            DeltaEvent.ToolCallStart,
            DeltaEvent.ToolCallDelta,
            DeltaEvent.ToolCallEnd,
            DeltaEvent.Complete,
            DeltaEvent.Error,
            DeltaEvent.Usage {
    // ... existing records unchanged ...
    record ToolCallStart(String toolCallId, String toolName) implements DeltaEvent {}
    record ToolCallDelta(String toolCallId, String jsonFragment) implements DeltaEvent {}
    record ToolCallEnd(ToolCall toolCall) implements DeltaEvent {}  // Note: carries complete ToolCall, not raw data
}
```

Note: `DeltaEvent.ToolCallEnd` wraps a complete `ToolCall` object (from `core.tool`), unlike `StreamEvent.ToolCallEnd` which carries raw fields. This is intentional — the StreamingChatService assembles the ToolCall via ToolCallAccumulator before emitting.

- [ ] **Step 4: Run tests** — verify no breakage
- [ ] **Step 5: Commit**

---

## Phase 5: Role & Message Extensions (Tasks 18-19)

### Task 18: Role — add TOOL

**Files:** Modify `core/provider/Role.java`, test `RoleTest.java`

- [ ] **Test + Implement**

```java
// Role.java — add one enum value
public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
```

Test: `Role.valueOf("TOOL")` succeeds, existing code unaffected.

- [ ] **Run full test suite**
- [ ] **Commit**

---

### Task 19: Message — extend record

**Files:** Modify `core/provider/Message.java`, test `MessageTest.java`

- [ ] **Step 1: Test**

```java
class MessageTest {
    @Test void backwardCompat() { Message m=new Message(Role.USER,"hello"); assertThat(m.content()).isEqualTo("hello"); assertThat(m.toolCalls()).isEmpty(); }
    @Test void assistantWithTools() { var tc=new ToolCall("id","read",Map.of()); var m=Message.assistantWithTools(List.of(tc)); assertThat(m.role()).isEqualTo(Role.ASSISTANT); assertThat(m.toolCalls()).contains(tc); assertThat(m.content()).isNull(); }
    @Test void toolResult() { var tr=ToolResult.success("ok","c"); var m=Message.toolResult("cid",tr); assertThat(m.role()).isEqualTo(Role.TOOL); assertThat(m.toolCallId()).isEqualTo("cid"); assertThat(m.toolResults()).contains(tr); }
}
```

- [ ] **Step 3: Modify** — expand `Message` record to 5-arg constructor with backward-compat 2-arg:

```java
public record Message(Role role, String content, List<ToolCall> toolCalls, List<ToolResult> toolResults, String toolCallId) {
    public Message(Role role, String content) { this(role, content, List.of(), List.of(), null); }
    public static Message assistantWithTools(List<ToolCall> toolCalls) { return new Message(Role.ASSISTANT,null,toolCalls,List.of(),null); }
    public static Message toolResult(String toolCallId, ToolResult result) { return new Message(Role.TOOL,null,List.of(),List.of(result),toolCallId); }
}
```

- [ ] **Step 4: Run ALL tests** — ensure no existing code breaks (all uses go through `new Message(role, content)` convenience constructor)
- [ ] **Step 5: Commit**

---

## Phase 6: SessionManager Extensions (Tasks 20-21)

### Task 20: SessionManager — add addToolMessages

**Files:** Modify `chat/session/SessionManager.java`, test `SessionManagerTest.java`

- [ ] **Test + Implement**

```java
// SessionManager.java — add method
void addToolMessages(List<ToolCall> toolCalls, List<ToolResult> toolResults);
```

Test: verify the method exists on interface, can be called.

- [ ] **Run tests**
- [ ] **Commit**

---

### Task 21: InMemorySessionManager — implement addToolMessages

**Files:** Modify `chat/session/InMemorySessionManager.java`, test `InMemorySessionManagerTest.java`

- [ ] **Step 1: Test**

```java
class InMemorySessionManagerTest {
    // ... existing tests ...
    @Test void shouldAddToolMessagesToHistory() {
        var mgr = new InMemorySessionManager();
        var tc = new ToolCall("id","read",Map.of("path","/x"));
        var tr = ToolResult.success("ok","c");
        mgr.addToolMessages(List.of(tc), List.of(tr));

        List<Message> hist = mgr.getHistory();
        assertThat(hist).hasSize(2);
        assertThat(hist.get(0).role()).isEqualTo(Role.ASSISTANT);
        assertThat(hist.get(0).toolCalls()).contains(tc);
        assertThat(hist.get(1).role()).isEqualTo(Role.TOOL);
        assertThat(hist.get(1).toolCallId()).isEqualTo("id");
    }
}
```

- [ ] **Step 3: Implement**

```java
@Override
public void addToolMessages(List<ToolCall> toolCalls, List<ToolResult> toolResults) {
    if (toolCalls != null && !toolCalls.isEmpty()) {
        messages.add(Message.assistantWithTools(toolCalls));
    }
    if (toolResults != null && !toolResults.isEmpty()) {
        // Note: toolCallId association — each ToolResult corresponds to its ToolCall
        // We store all results as a single TOOL message for simplicity (Anthropic convention)
        for (int i = 0; i < toolResults.size(); i++) {
            String tcId = i < toolCalls.size() ? toolCalls.get(i).id() : "unknown";
            messages.add(Message.toolResult(tcId, toolResults.get(i)));
        }
    }
}
```

- [ ] **Step 5: Commit**

---

## Phase 7: Provider Extensions (Tasks 22-24)

### Task 22: LlmProvider — add default streamChat with tools

**Files:** Modify `core/provider/LlmProvider.java`, test `LlmProviderTest.java`

- [ ] **Test + Implement**

```java
// LlmProvider.java — add default method
default StreamEventIterator streamChat(List<Message> history, LlmConfig config,
                                       List<ToolDefinition> toolDefs) {
    return streamChat(history, config); // forward-compat: ignore tools by default
}
```

Test: verify default impl delegates. Commit.

---

### Task 23: AnthropicProvider — tool_use support

**Files:** Modify `core/anthropic/AnthropicProvider.java`, test `AnthropicProviderToolUseTest.java`

- [ ] **Step 1: Test** — mock SSE stream with content_block_start(tool_use)→delta(input_json_delta)→content_block_stop, verify ToolCallStart/ToolCallDelta/ToolCallEnd sequence

- [ ] **Step 3: Implement**

1. Override `streamChat(history, config, toolDefs)` — inject `"tools"` field into request body:
   ```java
   // Anthropic format: tools: [{name:"read_file", description:"...", input_schema:{type:"object", properties:{...}, required:[...]}}]
   List<Map<String,Object>> anthropicTools = new ArrayList<>();
   for (ToolDefinition td : toolDefs) {
       Map<String,Object> t = new LinkedHashMap<>();
       t.put("name", td.name());
       t.put("description", td.description());
       t.put("input_schema", td.parameters());
       anthropicTools.add(t);
   }
   body.put("tools", anthropicTools);
   ```

2. In `parseSseEvent()`, add cases:
   - `"content_block_start"` with `block.type == "tool_use"` → `ToolCallStart(id, name)`
   - `"content_block_delta"` with `delta.type == "input_json_delta"` → `ToolCallDelta(id, partial_json)` — need to track current tool_use by index
   - `"content_block_stop"` → complete accumulation and emit `ToolCallEnd`

3. Internal: `Map<Integer, ToolAccum>` per content_block index. On content_block_start, store index→id mapping. On content_block_stop, look up by index.

4. In `buildRequestBody()`, when encountering `Role.TOOL` messages, convert to Anthropic's `tool_result` format (user role with tool_result content block).

- [ ] **Step 5: Commit**

---

### Task 24: OpenAIProvider — tool_calls support

**Files:** Modify `core/openai/OpenAIProvider.java`, test `OpenAIProviderToolCallsTest.java`

- [ ] **Step 1: Test** — mock SSE with tool_calls delta fragments, verify parsing

- [ ] **Step 3: Implement**

1. Override `streamChat(history, config, toolDefs)` — inject `"tools"` field:
   ```java
   // OpenAI format: tools: [{type:"function", function:{name:"read_file", description:"...", parameters:{...}}}]
   List<Map<String,Object>> oaiTools = new ArrayList<>();
   for (ToolDefinition td : toolDefs) {
       Map<String,Object> t = new LinkedHashMap<>();
       t.put("type", "function");
       Map<String,Object> f = new LinkedHashMap<>();
       f.put("name", td.name());
       f.put("description", td.description());
       f.put("parameters", td.parameters());
       t.put("function", f);
       oaiTools.add(t);
   }
   body.put("tools", oaiTools);
   ```

2. In `parseSseEvent()`, detect `delta.tool_calls`:
   - First appearance of index (function.name non-null) → `ToolCallStart(index+"", name)`
   - Subsequent appearances → `ToolCallDelta(index+"", arguments)`
   - When `choices[0].finish_reason == "tool_calls"` → emit `ToolCallEnd` for all active accumulators

3. In `buildRequestBody()`, convert `Role.TOOL` messages to OpenAI's tool message format.

- [ ] **Step 5: Commit**

---

## Phase 8: Config Extensions (Task 25)

### Task 25: Options — add tool configuration fields

**Files:** Modify `core/config/Options.java`, test `OptionsTest.java`

- [ ] **Step 1: Test**

```java
class OptionsTest {
    @Test void defaultToolOptions() { var o=new Options(); assertThat(o.toolSystemEnabled()).isTrue(); assertThat(o.commandExecutionEnabled()).isFalse(); assertThat(o.commandTimeoutSeconds()).isEqualTo(120); }
    @Test void customToolOptions() { var o=new Options(1000,"",false,true,60,15,500,10000,50); assertThat(o.toolSystemEnabled()).isFalse(); assertThat(o.commandExecutionEnabled()).isTrue(); }
}
```

- [ ] **Step 3: Modify** — extend `Options` record with fields:

```java
@JsonProperty("tool_system_enabled") boolean toolSystemEnabled,           // default true
@JsonProperty("command_execution_enabled") boolean commandExecutionEnabled, // default false
@JsonProperty("command_timeout_seconds") int commandTimeoutSeconds,       // default 120
@JsonProperty("file_operation_timeout_seconds") int fileOperationTimeoutSeconds, // default 30
@JsonProperty("read_file_max_lines") int readFileMaxLines,                // default 2000
@JsonProperty("command_output_max_chars") int commandOutputMaxChars,      // default 30000
@JsonProperty("search_max_results") int searchMaxResults                  // default 200
```

Provide the `Options()` no-arg and `Options(int maxTokens, String systemPrompt)` backward-compat constructors with defaults.

- [ ] **Step 5: Commit**

---

## Phase 9: AgentPromptBuilder (Task 26)

### Task 26: AgentPromptBuilder

**Files:** Create `chat/terminal/AgentPromptBuilder.java`, test `AgentPromptBuilderTest.java`

- [ ] **Step 1: Test**

```java
class AgentPromptBuilderTest {
    @Test void buildsWithAgentPromptOnly() { String p=AgentPromptBuilder.build(null); assertThat(p).contains("LavenderCode Agent"); assertThat(p).contains("Capabilities"); assertThat(p).contains("Rules"); }
    @Test void appendsUserPrompt() { String p=AgentPromptBuilder.build("Be concise."); assertThat(p).contains("User Instructions").contains("Be concise."); assertThat(p).startsWith("You are"); }
    @Test void handlesBlankUserPrompt() { String p=AgentPromptBuilder.build("  "); assertThat(p).doesNotContain("User Instructions"); }
}
```

- [ ] **Step 3: Implement** — 4-layer tower: Identity→Capabilities→Rules→User Custom. (See TECH §4.6 for full prompt text)

- [ ] **Step 5: Commit**

---

## Phase 10: TUI Rendering (Tasks 27-30)

### Task 27: RenderEvent — add ToolCallRender and ToolResultRender

**Files:** Modify `chat/terminal/RenderEvent.java`, test `RenderEventToolTest.java`

- [ ] **Step 1: Test**

```java
class RenderEventToolTest {
    @Test void toolCallRender() { var e=new RenderEvent.ToolCallRender("id","read",Map.of("path","/x"),"running"); assertThat(e.status()).isEqualTo("running"); }
    @Test void toolResultRender() { var e=new RenderEvent.ToolResultRender("id","ok",true,100); assertThat(e.success()).isTrue(); assertThat(e.contentLength()).isEqualTo(100); }
}
```

- [ ] **Step 3: Modify** — add to `permits` clause and define:

```java
record ToolCallRender(String toolCallId, String toolName, Map<String,Object> params, String status) implements RenderEvent {}
record ToolResultRender(String toolCallId, String summary, boolean success, int contentLength) implements RenderEvent {}
```

- [ ] **Step 5: Commit**

---

### Task 28: MessageBlock — add ToolRowSegment

**Files:** Modify `chat/terminal/MessageBlock.java`, test `MessageBlockToolRowTest.java`

- [ ] **Step 1: Test**

```java
class MessageBlockToolRowTest {
    @Test void toolRowSegmentRendersCorrectly() {
        MessageBlock mb = new MessageBlock(Role.ASSISTANT);
        mb.appendToolRow("read_file", "config.yaml", "running", null, false, 80);
        var lines = mb.allLines();
        assertThat(lines).isNotEmpty();
        // First line should contain "● Read" or tool indicator
    }
    @Test void toolRowWithResult() {
        MessageBlock mb = new MessageBlock(Role.ASSISTANT);
        mb.appendToolRow("read_file", "config.yaml", "done", "12 行·config.yaml", true, 80);
        mb.appendToolRow("execute_command", "mvn test", "done", "失败 (exit 1)", false, 80);
        var lines = mb.allLines();
        assertThat(lines.size()).isGreaterThanOrEqualTo(4); // 2 tools × 2 lines each
    }
}
```

- [ ] **Step 3: Implement**

In `MessageBlock`, add `ToolRowSegment` inner class and `appendToolRow()` method:

```java
// New segment class
private static final class ToolRowSegment extends Segment {
    final String toolName;
    final String paramsSummary;
    final String resultSummary;
    final boolean success;
    ToolRowSegment(String toolName, String paramsSummary, String resultSummary, boolean success) {
        this.toolName = toolName;
        this.paramsSummary = paramsSummary;
        this.resultSummary = resultSummary;
        this.success = success;
    }
}

// New public method
public int appendToolRow(String toolName, String paramsSummary, String status,
                          String resultSummary, boolean success, int terminalWidth) {
    linesDirty = true;
    int oldCount = lineCount();
    ToolRowSegment seg = findOrCreateToolRow(toolName);
    if (resultSummary != null) seg.resultSummary = resultSummary;
    // Rebuild lines for this segment
    rebuildToolRowLines(seg, paramsSummary, status, terminalWidth);
    recalcLineCount();
    return lineCount() - oldCount;
}
```

Rendering style: `● Read(config.yaml)` in gray, result summary in green (success) or red (failure).

- [ ] **Step 5: Commit**

---

### Task 29: TerminalRenderer — handle tool row events

**Files:** Modify `chat/terminal/TerminalRenderer.java`

- [ ] **Step 3: Implement** — add cases in the render loop for:

```java
case RenderEvent.ToolCallRender tcr -> {
    // Phase 1: render placeholder "● Read → 准备中…"
    // Phase 2: update to "● Read(path) → 执行中…"
    currentMessage.appendToolRow(tcr.toolName(), formatParams(tcr.params()), tcr.status(), null, true, terminalWidth);
    needsRedraw = true;
}
case RenderEvent.ToolResultRender trr -> {
    currentMessage.appendToolRow("", "", "done", trr.summary(), trr.success(), terminalWidth);
    needsRedraw = true;
}
```

Helper `formatParams()`: extract key param (path/cmd/pattern) based on tool name.

- [ ] **Run ALL existing TUI tests**
- [ ] **Commit**

---

### Task 30: TerminalRenderer — scrollback includes tool rows

**Files:** No new files, part of Task 29

- [ ] **Test:** after tool rows rendered + message finalized, verify `scrollTo(0)` + scroll events include tool rows in visible area
- [ ] **Verify:** `MessageBlock.allLines()` includes ToolRowSegment lines alongside ContentSegment lines
- [ ] **Commit**

---

## Phase 11: StreamingChatService Modifications (Task 31)

### Task 31: StreamingChatService — toDeltaEvent handles tool calls

**Files:** Modify `chat/terminal/StreamingChatService.java`, test `StreamingChatServiceToolTest.java`

- [ ] **Step 1: Test**

```java
class StreamingChatServiceToolTest {
    @Test void toolCallEventsDelegatedToAccumulator() {
        var svc = new StreamingChatService();
        // Mock provider returning tool events
        // Verify that ToolCallStart/ToolCallDelta/ToolCallEnd produce correct DeltaEvents
    }
}
```

- [ ] **Step 3: Modify `toDeltaEvent()`**

```java
private final ToolCallAccumulator toolCallAccumulator = new ToolCallAccumulator();

private DeltaEvent toDeltaEvent(StreamEvent se) {
    return switch (se) {
        case StreamEvent.ContentDelta cd  -> new DeltaEvent.Content(cd.text());
        case StreamEvent.ThinkingDelta td -> null;
        case StreamEvent.ToolCallStart tcs -> {
            toolCallAccumulator.start(tcs.toolCallId(), tcs.toolName());
            yield new DeltaEvent.ToolCallStart(tcs.toolCallId(), tcs.toolName());
        }
        case StreamEvent.ToolCallDelta tcd -> {
            toolCallAccumulator.append(tcd.toolCallId(), tcd.jsonFragment());
            yield null;
        }
        case StreamEvent.ToolCallEnd tce -> {
            ToolCall call = toolCallAccumulator.complete(tce.toolCallId());
            yield new DeltaEvent.ToolCallEnd(call);
        }
        case StreamEvent.StreamComplete sc -> new DeltaEvent.StreamComplete();
        case StreamEvent.StreamError err  -> new DeltaEvent.Error(err.message(), err.statusCode());
    };
}
```

- [ ] **Step 4: Run ALL tests**
- [ ] **Step 5: Commit**

---

## Phase 12: NetworkOrchestrator State Machine (Tasks 32-36)

### Task 32: NetworkOrchestrator — add ToolPhase enum and fields

**Files:** Modify `chat/terminal/NetworkOrchestrator.java`

- [ ] **Step 1: Test** — `NetworkOrchestratorToolTest.java` verifies state transitions

- [ ] **Step 3: Add fields**

```java
private enum ToolPhase { IDLE, STREAMING, EXECUTING, REINJECTING, STREAMING_FINAL, DONE }
private ToolPhase toolPhase = ToolPhase.IDLE;
private final List<ToolCall> completedToolCalls = new ArrayList<>();
private final ToolCallAccumulator toolAccumulator = new ToolCallAccumulator();
private final Options options; // injected via constructor (new param)
```

- [ ] **Step 5: Commit**

---

### Task 33: NetworkOrchestrator — STREAMING phase handles tool deltas

**Files:** Same file — modify `onDeltaReceived()`

- [ ] **Step 3: Modify** — add tool call handling to the delta callback:

```java
private void onDeltaReceived(RequestContext ctx, DeltaEvent delta) {
    if (currentRequest.get() != ctx) return;
    switch (toolPhase) {
        case IDLE, STREAMING -> {
            switch (delta) {
                case DeltaEvent.ToolCallStart tcs -> {
                    safePut(new RenderEvent.ToolCallRender(tcs.toolCallId(), tcs.toolName(), Map.of(), "preparing"));
                }
                case DeltaEvent.ToolCallEnd tce -> {
                    completedToolCalls.add(tce.toolCall());
                    safePut(new RenderEvent.ToolCallRender(
                        tce.toolCall().id(), tce.toolCall().name(), tce.toolCall().parameters(), "running"));
                }
                case DeltaEvent.Content c -> deltaBuffer.append(/* existing logic */);
                case DeltaEvent.Complete -> {
                    if (completedToolCalls.isEmpty()) { finishNormally(ctx); }
                    else { toolPhase = ToolPhase.EXECUTING; executeAllTools(); }
                }
                case DeltaEvent.Error e -> { /* existing error logic */ }
                // ... Usage same
            }
        }
    }
}
```

- [ ] **Step 5: Commit**

---

### Task 34: NetworkOrchestrator — EXECUTING phase serial tool execution

**Files:** Same file — add `executeAllTools()` method

- [ ] **Step 3: Implement**

```java
private void executeAllTools() {
    List<ToolResult> results = new ArrayList<>();
    for (ToolCall tc : completedToolCalls) {
        // Check user cancellation
        if (currentRequest.get() == null) { toolPhase = ToolPhase.IDLE; return; }

        Tool tool = ToolRegistry.get(tc.name());
        if (tool == null) {
            var err = ToolResult.error("TOOL_NOT_FOUND","工具未注册·"+tc.name(), tc.name());
            results.add(err);
            safePut(new RenderEvent.ToolResultRender(tc.id(), err.summary(), false, 0));
            continue;
        }

        // Execute with timeout
        long timeout = "execute_command".equals(tc.name()) ? options.commandTimeoutSeconds() : options.fileOperationTimeoutSeconds();
        ToolResult result = executeWithTimeout(tool, tc.parameters(), timeout);
        results.add(result);

        // Render result
        safePut(new RenderEvent.ToolResultRender(tc.id(), result.summary(), result.success(),
            result.content() != null ? result.content().length() : 0));
    }

    // Check user cancellation again before re-injecting
    if (currentRequest.get() == null) { toolPhase = ToolPhase.IDLE; return; }

    toolPhase = ToolPhase.REINJECTING;
    reInjectAndContinue(results);
}

private ToolResult executeWithTimeout(Tool tool, Map<String,Object> params, long timeoutSec) {
    try {
        return CompletableFuture.supplyAsync(() -> tool.execute(params))
            .orTimeout(timeoutSec, TimeUnit.SECONDS)
            .exceptionally(ex -> ToolResult.error("TIMEOUT",
                "超时 ("+timeoutSec+"s)·"+tool.name(), ex.getMessage()))
            .get();
    } catch (Exception e) {
        return ToolResult.error("TOOL_ERROR", e.getMessage() != null ? e.getMessage() : "未知错误", e.getClass().getSimpleName());
    }
}
```

- [ ] **Step 5: Commit**

---

### Task 35: NetworkOrchestrator — REINJECTING → STREAMING_FINAL

**Files:** Same file — add `reInjectAndContinue()` method

- [ ] **Step 3: Implement**

```java
private void reInjectAndContinue(List<ToolResult> results) {
    // Add tool messages to session
    sessionManager.addToolMessages(completedToolCalls, results);

    // Clear accumulated tool calls for next round
    completedToolCalls.clear();
    toolAccumulator.clear();

    // Build system prompt with agent instructions
    String systemPrompt = AgentPromptBuilder.build(config.options().systemPrompt());

    // Send follow-up request with updated history
    toolPhase = ToolPhase.STREAMING_FINAL;
    var ctxRef = new AtomicReference<RequestContext>();
    try {
        List<ToolDefinition> toolDefs = ToolRegistry.export();
        ctxRef.set(chatService.submit(provider, sessionManager.getHistory(), config, toolDefs,
            delta -> onDeltaReceived(ctxRef.get(), delta)));
        currentRequest.set(ctxRef.get());
    } catch (Exception e) {
        toolPhase = ToolPhase.IDLE;
        safePut(new RenderEvent.AddSystemMessage("[Error] " + e.getMessage()));
        safePut(new RenderEvent.FinalizeMessage());
    }
}
```

Note: `ChatService.submit()` signature expands to accept `List<ToolDefinition>`. Update `ChatService` interface:

```java
default RequestContext submit(LlmProvider provider, List<Message> history, LlmConfig config,
                               List<ToolDefinition> toolDefs, Consumer<DeltaEvent> onDelta) {
    return submit(provider, history, config, onDelta); // backward compat
}
```

- [ ] **Step 5: Commit**

---

### Task 36: NetworkOrchestrator — STREAMING_FINAL ignores tool calls, handles cancel

**Files:** Same file — modify `onDeltaReceived()` for STREAMING_FINAL phase

- [ ] **Step 3: Add case**

```java
case STREAMING_FINAL -> {
    switch (delta) {
        case DeltaEvent.Content c -> deltaBuffer.append(/* normal */);
        case DeltaEvent.ToolCallStart tcs,
             DeltaEvent.ToolCallEnd tce -> {
            // Silently ignore — single-round closed loop
        }
        case DeltaEvent.Complete -> {
            toolPhase = ToolPhase.DONE;
            finishNormally(ctx); // same as existing Complete handler
            toolPhase = ToolPhase.IDLE;
        }
        case DeltaEvent.Error e -> { /* existing error logic */ toolPhase = ToolPhase.IDLE; }
    }
}
```

- [ ] **Step 4: User cancellation during EXECUTING**

```java
// In executeAllTools(), add check at start of loop:
if (currentRequest.get() == null) {
    // User cancelled — immediate termination per Run lifecycle
    toolPhase = ToolPhase.IDLE;
    completedToolCalls.clear();
    safePut(new RenderEvent.AddSystemMessage("[Cancelled]"));
    safePut(new RenderEvent.FinalizeMessage());
    return;
}
```

- [ ] **Step 5: Commit**

---

## Phase 13: Wiring & Integration (Tasks 37-39)

### Task 37: First-round ChatService.submit passes tool definitions

**Files:** Modify `ChatService.java` (interface + StreamingChatService impl)

- [ ] **Modify `ChatService` interface**: add default method with `toolDefs` parameter
- [ ] **Modify `StreamingChatService`**: `submit()` calls `provider.streamChat(history, config, toolDefs)` when `toolDefs` is non-empty
- [ ] **Modify `NetworkOrchestrator.handleSendMessage()`**: call `ToolRegistry.export()` and pass to submit

```java
// In handleSendMessage:
List<ToolDefinition> toolDefs = options.toolSystemEnabled() ? ToolRegistry.export() : List.of();
if (!toolDefs.isEmpty()) {
    ctxRef.set(chatService.submit(provider, sessionManager.getHistory(), config, toolDefs, delta -> onDeltaReceived(...)));
} else {
    ctxRef.set(chatService.submit(provider, sessionManager.getHistory(), config, delta -> onDeltaReceived(...)));
}
```

- [ ] **Commit**

---

### Task 38: LavenderCode.main() — tool registration & ServiceLoader

**Files:** Modify `LavenderCode.java`

- [ ] **Step 3: Add registration**

```java
// After provider selection, before TerminalChatApplication creation:
Options opts = config.options();
if (opts.toolSystemEnabled()) {
    // Register tools with configured limits
    ToolRegistry.register(new ReadFileTool(opts.readFileMaxLines()));
    ToolRegistry.register(new WriteFileTool());
    ToolRegistry.register(new EditFileTool());
    ToolRegistry.register(new ExecuteCommandTool(opts.commandExecutionEnabled(),
        opts.commandTimeoutSeconds(), opts.commandOutputMaxChars()));
    ToolRegistry.register(new GlobTool(opts.searchMaxResults()));
    ToolRegistry.register(new GrepTool(opts.searchMaxResults()));
}
```

- [ ] **Pass `Options` to `NetworkOrchestrator` constructor**
- [ ] **Commit**

---

### Task 39: End-to-end integration validation

**Files:** Create `src/test/java/com/lavendercode/integration/ToolSystemIntegrationTest.java`

- [ ] **Test scenarios** (using MockWebServer to simulate LLM responses):

1. **No-tool path** — model returns plain text, no tool calls → message appears normally
2. **Single tool** — model calls read_file → tool executes → result injected → model answers
3. **Multi-tool** — model calls read_file + search_content → both execute serially → results injected → answer
4. **Single-round boundary** — during STREAMING_FINAL, model tries to call tool → ignored, answer displayed
5. **Tool error** — read_file fails with FILE_NOT_FOUND → error shown in UI → model adapts answer
6. **User cancel during execution** — Ctrl+C during tool exec → session shows [Cancelled], no re-injection

- [ ] **Run full test suite** — `mvn test` — ensure ALL tests pass
- [ ] **Commit**

---

## Phase 14: Cleanup & Verification (Task 40)

### Task 40: Full regression suite

- [ ] **Run:** `mvn clean test`
- [ ] **Verify:** zero test failures
- [ ] **Verify:** existing non-tool conversations work unchanged (backward compat)
- [ ] **Verify:** all 13 acceptance criteria (AC-S1 through AC-S13) are met
- [ ] **Commit any fixups**

---

## Acceptance Criteria Mapping

| AC | Met By Tasks |
|:---|:---|
| AC-S1 (F1 注册中心) | Tasks 8, 38 |
| AC-S2 (F2 读) | Task 10 |
| AC-S3 (F2 写) | Task 11 |
| AC-S4 (F2 改) | Task 12 |
| AC-S5 (F2 执行) | Task 13 |
| AC-S6 (F2 找/搜) | Tasks 14, 15 |
| AC-S7 (F3/F4 流式解析) | Tasks 16, 17, 31 |
| AC-S8 (F5/F6 端到端) | Tasks 32-37, 39 |
| AC-S9 (F6 单轮边界) | Task 36 |
| AC-S10 (F7/N3 跨协议) | Tasks 23, 24, 39 |
| AC-S11 (F8 工具行) | Tasks 27-30 |
| AC-S12 (F9/N4 错误) | Tasks 3, 34, 39 |
| AC-S13 (N5 体量控制) | Tasks 2, 10, 13, 14, 15 |
