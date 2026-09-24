# 快速开始

本页说明如何获得 stage-0 编译器并运行第一个 Sprig 程序。

## 安装方式

**方式 A：下载 Alpha 发行包**（包含编译器、runtime、ANTLR 与启动脚本，不含 JDK）：

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.1.0-alpha.1/sprig-v0.1.0-alpha.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.1.0-alpha.1-jdk.zip.sha256
unzip sprig-v0.1.0-alpha.1-jdk.zip
cd sprig-v0.1.0-alpha.1-jdk
./bin/sprig run examples/hello.spr
```

**方式 B：从源码构建**，见下文。

## 环境要求

| 工具 | 版本 | 用途 |
|---|---|---|
| JDK | 17 或更新 | 编译器以 `javac --release 17` 构建，已在 OpenJDK 17.0.19 与 26.0.1（macOS Apple Silicon）上端到端运行；托管 CI 在 Linux 上覆盖两个版本。 |
| Python | 3.12 或更新 | 测试与验收脚本。 |
| `curl` | 任意 | 首次构建时下载固定版本的 ANTLR 4.13.2 工具 JAR。 |
| Node.js | 20 或更新 | 仅在本地构建本文档站时需要。 |

## 构建编译器

```bash
git clone https://github.com/ColinHouse/Sprig.git
cd Sprig
./scripts/build.sh
```

当 `tools/antlr-4.13.2-complete.jar` 不存在时，脚本会从 Maven Central
下载它并校验 SHA-256，然后从 `grammar/` 重新生成解析器，用
`javac --release 17` 编译 `compiler/` 和 `runtime/`，最后生成 `bin/sprig`。

```text
Generating ANTLR4 parser...
Compiling compiler + runtime...
Built Sprig stage-0 compiler.
  launcher:  .../bin/sprig
  classes:   .../build/classes
  compiler:  .../build/sprig-compiler.jar
```

## 运行第一个程序

<<< @/../examples/hello.spr

```bash
./bin/sprig run examples/hello.spr
```

```text
Hello, Ada!
```

`run` 会先做类型检查，再生成 Java 源码、调用 `javac`，最后在 JVM 上执行。

## 命令行

```text
Usage: sprig <command> [options]

  check <file.spr> [--json] [--syntax-only]   parse and type-check
  run   <file.spr> [--json] [--keep] [-- a b] compile and execute on the JVM
  build <file.spr> [-d dir] [--json]          emit Java sources + .class files
  explain <SPR-CODE>                          explain a diagnostic code
  codes [--json]                              list every diagnostic code
  version
```

| 命令 | 作用 |
|---|---|
| `check` | 运行词法、缩进、解析、名称解析与类型检查，不写任何文件。 |
| `run` | 在 `check` 之后生成 Java、调用 `javac` 并运行程序。 |
| `build` | 把生成的 Java 源码与 `.class` 文件写入 `-d <dir>`（默认 `build/out`）。 |
| `explain` | 解释一个稳定的诊断码，例如 `SPR-MATCH-NONEXHAUSTIVE`。 |
| `codes` | 列出全部诊断码。 |
| `--json` | 把结果封装为单个机器可读的 JSON 文档。 |
| `--syntax-only` | 只做词法、缩进与解析。 |

JSON 结果与稳定诊断码见[工具与 JSON](/guide/tooling)。

## 运行项目测试

```bash
./scripts/test.sh
```

该脚本会运行语法正反例、46 个语义期望用例、带 golden stdout 的运行时程序、
visitor 程序、示例、数值边界、解析器恢复模糊测试以及独立验收矩阵，最后输出
`N passed, 0 failed`，任何失败都会以非零状态退出。

独立的语法 smoke harness：

```bash
ANTLR_JAR="$PWD/tools/antlr-4.13.2-complete.jar" ./tools/test-grammar.sh
```

## 常见问题

- **提示 `JDK required`**：安装 JDK 17 或更新版本，并确保 `java` 与 `javac` 在 `PATH` 中。
- **ANTLR 校验和不匹配**：删除 `tools/antlr-4.13.2-complete.jar` 后重新构建，让脚本重新下载。
- **`SPR-LEX-TAB`**：Sprig 的缩进只能使用空格，不能使用制表符。
- **Java 引用结果被报告为可空**：这是有意设计；请先用 `!= null` 判空再调用方法。
