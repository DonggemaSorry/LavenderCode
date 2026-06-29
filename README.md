# LavenderCode

> 薰衣草色的终端里，代码如诗。

LavenderCode 是一盏栖居在命令行中的 AI 编程之灯——它读你文件里的字句，搜你目录间的行迹，在你敲下每一个回车时，把遥远的语言模型之力，折叠成一行温暖的回复。

等你，在薰衣草盛开的地方。

---

## 她是什么

一个跑在终端里的 AI 编程助手，就像你熟悉的那些工具一样。但她住在本地，亲手触碰你的文件系统，替你看、替你写、替你改。

她的名字取自薰衣草的淡紫——代码编辑器里第 6 色调色板的那一抹温柔。

---

## 她能做什么

- **读** — 翻开你的代码，逐行编号，娓娓道来
- **写** — 落笔成文，自动铺好未曾存在的目录
- **改** — 寻一个独一无二的旧句子，换成新的
- **搜** — 在文件森林里循着 glob 的足迹，或用 grep 的眼在字里行间穿行
- **执行** — 替你敲下 shell 命令，把终端的回响带回她的话语里

---

## 启动方式

### 准备

需要 **Java 21** 和 **Maven 3.8+**。

```bash
# 1. 克隆
git clone https://github.com/DonggemaSorry/LavenderCode.git
cd LavenderCode

# 2. 配置
cp config.yaml.example config.yaml
# 编辑 config.yaml，填入你的 API 密钥
```

`config.yaml` 示例：

```yaml
providers:
  - protocol: openai       # 或 anthropic
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: your-key-here

options:
  max_tokens: 4096
  tool_system_enabled: true           # 工具调用开关
  command_execution_enabled: false    # shell 命令执行（谨慎开启）
  command_timeout_seconds: 120
  file_operation_timeout_seconds: 30
  read_file_max_lines: 2000
  search_max_results: 200
```

### 编译 & 运行

```bash
# 编译
mvn clean package

# 方式一：直接运行 fat jar
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar

# 方式二：Maven 启动
mvn exec:java

# 方式三：指定配置文件
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar --config /path/to/config.yaml

# 跳过开场动画
java -Dlavendercode.skipSplash=true -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 终端操作

| 操作 | 快捷键 |
|---|---|
| 发送消息 | Enter |
| 换行 | Ctrl+J |
| 取消当前请求 | Ctrl+C |
| 退出 | Ctrl+D 或 /exit |
| 清空对话 | /clear |
| 查看帮助 | /help |

需要的仅是 Java 21 和一颗愿意让 AI 触碰文件的心。

---

## 她追随的星光

她能与任何说 OpenAI 语言的模型对话——Claude、DeepSeek、以及所有支持 tools 的 API。

支持 Anthropic 的 `system` 字段与 `thinking` 扩展；OpenAI 兼容协议的流式 SSE 解析与 tool_calls 增量累积。

---

> *Write code like poetry, and let the terminal bloom lavender.*
