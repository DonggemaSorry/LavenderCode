# LavenderCode

> 薰衣草色的终端里，代码如诗。

LavenderCode 是一盏栖居在命令行中的 AI 编程之灯——它读你文件里的字句，搜你目录间的行迹，在你敲下每一个回车时，把遥远的语言模型之力，折叠成一行温暖的回复。

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

## 如何与她相遇

```bash
# 取一份地图
cp config.yaml.example config.yaml
# 填入你的密钥
# ...

# 编译她的身影
mvn clean package

# 在薰衣草的帷幕升起时，与她对话
java -jar target/lavendercode-1.0-SNAPSHOT-jar-with-dependencies.jar
```

需要的仅是 Java 21 和一颗愿意让 AI 触碰文件的心。

---

## 她追随的星光

她能与任何说 OpenAI 语言的神明对话——Claude、DeepSeek、以及所有支持 tools 的 API。

```yaml
providers:
  - protocol: openai       # 或 anthropic
    model: deepseek-chat
    base_url: https://api.deepseek.com
    api_key: your-key-here
```

---

> *Write code like poetry, and let the terminal bloom lavender.*
