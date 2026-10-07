# 1. 入门

这一章把工具装好，写第一个程序，认识几条以后天天要用的命令。

## 1.1 安装

Sprig 把你的代码翻译成 Java，再交给 JVM 运行，所以需要 **JDK 21 或更新版本**（注意是 JDK，不是只有 JRE）。先确认：

```bash
java -version
javac -version
```

两条都有输出，再装 Sprig SDK。Linux 和 macOS 上用安装脚本：

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig version
```

Windows、手动下载和从源码构建的方法见[快速开始](/guide/getting-started)。装好以后，`sprig doctor` 会检查 JDK 和编译器是否都能找到。

## 1.2 第一个程序

新建一个文件 `hello.spr`：

<<< @/snippets/book/ch01_hello.spr

运行：

```bash
sprig run hello.spr
```

```text
你好，Sprig
3
```

两件事值得注意。

第一，**没有 `main`**。文件里的顶层语句就是程序，从上到下执行。你当然可以写一个 `main` 函数，但得自己调用它（第 4 章再说）。

第二，`sprig run` 其实做了四件事：检查代码、生成 Java、用 `javac` 编译、启动 JVM。第一次运行一个程序大约要两秒，大部分时间花在 `javac` 上；没改过的程序再次运行时会跳过这一步。

## 1.3 只检查，不运行

写代码时你会更常用另一条命令：

```bash
sprig check hello.spr
```

它只做检查，一次列出所有错误，不生成任何东西，比 `run` 快得多。把 `hello.spr` 里的 `print(1 + 2)`（第 3 行）改成 `print(1 + "a" - 2)` 再检查，就能看到 Sprig 报错的样子：

```text
SPR-NUM-MIXED [TYPE] hello.spr:3:7: Operator '-' has no implicit conversion between String and Int (expected matching numeric families, actual String and Int)
  hint: Convert deliberately with an exact or explicitly lossy numeric method.
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

读法是固定的：错误码、类别、文件和行列位置、一句话说明，有时还有一行 `hint:` 告诉你怎么改。这本书后面会反复用到这个格式。

## 1.4 建一个项目

单个文件够用一阵子。代码多了，或者要用别人的库，就建项目：

```bash
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

```text
Hello, Sprig!
```

`sprig init` 生成两个文件：

```text
my-tool/
├── sprig.toml        # 项目配置
└── src/
    └── main.spr      # 程序入口
```

`sprig resolve` 解析依赖并写出锁文件 `sprig.lock`，只在改了 `sprig.toml` 之后才需要重新运行。在项目目录里，`sprig run` 和 `sprig check` 不用再写文件名。

本书第 2 到第 11 章的例子都是单文件，用 `sprig run 文件名` 即可；第 12 章回来细讲项目。

## 1.5 常用命令一览

| 命令 | 用途 |
|---|---|
| `sprig check [文件]` | 只检查，一次列出所有错误 |
| `sprig run [文件]` | 检查并运行 |
| `sprig explain <错误码>` | 解释一个错误码：原因、修法、正反例 |
| `sprig help [主题]` | 语法速查，比如 `sprig help nullability`；不带主题列出所有主题 |
| `sprig fmt <文件或目录>` | 统一格式 |
| `sprig doctor` | 检查环境 |

几乎每条命令都能加 `--json`，输出结构化结果。第 15 章专门讲怎么把这些命令用好。

## 小结

- 顶层语句就是程序；`sprig run` 运行，`sprig check` 只检查。
- 报错格式：错误码、位置、说明、提示。遇到不懂的错误码就 `sprig explain`。
- 项目由 `sprig.toml` 定义，`sprig init` 创建，`sprig resolve` 解析依赖。

下一章开始写真正的代码：[值与类型](/tutorial/ch02-values)。
