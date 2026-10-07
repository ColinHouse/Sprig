# 工具与 JSON

Sprig 只有一个命令行程序 `sprig`，所有功能都是它的子命令。它没有常驻后台的进程，连语言服务器也是一个子命令 `sprig lsp`，由编辑器自己启动。VS Code 里的支持见 [VS Code 插件](/guide/editor)。

这页列出的命令，已发布的 v0.7.1-beta.1 里都有。你装的 SDK 具体支持哪些功能，以 `sprig capabilities --json` 的输出为准。

## 命令一览

写代码时最常用的：

| 命令 | 作用 |
|---|---|
| `sprig check [文件]` | 只检查、不运行，一次列出所有错误 |
| `sprig run [文件] [-- 参数]` | 检查、编译并运行，`--` 后面的内容会传给程序 |
| `sprig test [路径]` | 运行项目测试，可以用 `--filter` 按名字筛选 |
| `sprig build [文件]` | 生成 Java 源码和 class 文件 |
| `sprig build --bundle [--archive]` | 打出一个自带 Java 运行时的目录，没装 JDK 的机器也能运行，见下面 |
| `sprig fmt <文件或目录>` | 格式化代码，加 `--check` 只检查、不修改 |

项目和依赖：

| 命令 | 作用 |
|---|---|
| `sprig init [目录]` | 新建项目 |
| `sprig resolve` | 解析依赖，写入 `sprig.lock` |
| `sprig add`、`sprig remove` | 添加、删除依赖，见[项目](/guide/projects) |
| `sprig project` | 查看项目信息 |
| `sprig deps` | 列出声明的依赖 |

查资料：

| 命令 | 作用 |
|---|---|
| `sprig explain <错误码>` | 解释一个错误码：原因、修法、正反例 |
| `sprig codes` | 列出所有错误码 |
| `sprig help [主题]` | 语法速查；不带主题时会列出所有主题 |
| `sprig api <Java 类>` | 查看一个 Java 类在 Sprig 里的签名 |
| `sprig api <模块.spr>` | 查看一个 Sprig 模块对外提供哪些声明，以及每个声明上方写的注释 |
| `sprig capabilities` | 查看当前编译器实现了哪些功能 |
| `sprig doctor` | 检查 JDK 和编译器等环境 |

其他：

| 命令 | 作用 |
|---|---|
| `sprig wrap <Java 类> --out <文件>` | 为 Java 类生成 Sprig 包装代码，见 [JVM 互操作](/guide/jvm-interop) |
| `sprig upgrade` | 升级 SDK；加 `--check` 只看有没有新版本 |
| `sprig lsp` | 给编辑器用的语言服务器，见[语言服务器](#语言服务器) |
| `sprig version` | 显示版本号 |

在项目里，`check`、`run`、`build` 可以不写文件名，默认使用项目的入口。`check`、`build`、`run`、`api`、`wrap` 和 `doctor` 都能用 `--classpath` 加入本地的 JAR 或目录，可以写多次。这个参数只使用你给的路径，不会下载任何东西。

## 几个常用选项

- **`check --syntax-only`**：`check` 本来就不生成代码；加上这个选项更快，只检查词法、缩进和语法。
- **`run --keep`**：保留生成的 Java 文件，方便查看。
- **`run --no-cache`**：即使同一个程序之前跑过，也重新调用 javac。默认情况下 `run` 和 `test` 会把每个程序编译出的 class 文件留在 `~/.sprig/cache/javac` 下（保留最近的 64 个），键由生成的 Java、编译器和 Java 版本、运行时和 classpath 决定，所以再次运行没改过的程序会跳过 javac。真要调用 javac 时，它也只编译你的程序：运行时已经随 SDK 编译好，直接复制到你的 class 文件旁边。如果你用的 JDK 和构建 SDK 的不是同一个版本，第一次运行会把运行时编译一次，放进 `~/.sprig/cache/runtime`。设置 `SPRIG_JAVAC_CACHE=off` 关闭程序的缓存，设成一个目录路径则换个位置。
- **`run --stacktrace`**：程序运行时出了没被捕获的错误，Sprig 会报 `SPR-RUNTIME-ERROR` 或 `SPR-RUNTIME-EXCEPTION`，并指出是源码的哪一行。需要完整的 JVM 堆栈时，加上这个选项。
- **`build -d <目录>`**：`build` 默认输出到 `sprig-build/`，`-d` 可以换个目录。检查没通过时不会生成 class 文件。
- **`build --emit-java-only`**：只做静态检查和生成 Java，不调用 javac。加 `--json` 时，结果里会有 `javaSources`、`mainClass` 和 `javacInvoked: false`。
- **`build --bundle`**：在 `build` 的输出目录里再写一个 `<名字>/` 目录（名字是 `--bin`、项目名或文件名），交给没装 Java 的人也能运行：`bin/<名字>` 是 POSIX sh 启动器，`bin/<名字>.cmd` 是 Windows 启动器；`lib/` 里是程序的 jar、Sprig 运行时和锁文件里的全部 jar（Maven 依赖也在，按坐标命名）；`runtime/` 是用 jlink 从这些 jar 实际用到的模块做出来的 Java 运行时镜像，`runtime/legal/` 里的 JDK 许可声明原样保留（OpenJDK 的 GPLv2 + Classpath Exception 允许连同声明一起分发）。启动器在你当前的目录里运行程序，原样转发参数和退出码，并把程序自己的类做成 class-data-sharing 归档放在用户缓存目录里，第二次启动更快。打包时不会运行你的程序。**镜像只能在构建它的操作系统和 CPU 架构上运行**，命令输出会写明是哪个平台；要给别的平台就在那个平台上构建。加 `--archive` 会在旁边再写一个 `<名字>.zip`，解压后启动器照样可执行。需要完整的 JDK（有 `jdeps`、`jlink` 和 `jmods/`）：缺工具报 `SPR-BUNDLE-TOOLS`，`jdeps` 分析失败报 `SPR-BUNDLE-JDEPS`，没有 `jmods/` 或 jlink 失败报 `SPR-BUNDLE-LAYOUT`，每个都带修法。详见 [bundle 说明（英文）](/en/reference/projects/bundle)。

每个错误码的含义都可以用 `sprig explain` 查，完整列表见[错误码（英文）](/en/reference/tooling/diagnostic-codes)。

## JSON 输出

几乎每个命令都能加 `--json`。加了以后，标准输出里只有一个 JSON 文档，程序运行失败时也是这样。程序打印的内容放在 `programOutput` 里，错误放在 `diagnostics` 里，两者不会混在一起。从 v0.6.0-beta.1 开始，程序写到标准错误的内容放在 `programErrorOutput` 里；用 `--json` 运行时，程序读到的标准输入是空的。

成功运行一个打招呼的小程序：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.7.1-beta.1",
  "command": "run",
  "exitCode": 0,
  "programOutput": "Hello, Ada!\n",
  "environment": {"classpath": []},
  "diagnostics": []
}
```

再看一个检查失败的例子。下面这段代码漏掉了 `Square`：

<<< @/snippets/guide/tooling_missing_case.spr

普通输出是这样的：

```text
SPR-MATCH-NONEXHAUSTIVE [FLOW] main.spr:6:12: Missing case: Shape.Square
  hint: Add 'case Shape.Square:' (there is no default case)
1 error(s); run 'sprig explain <code>' for details on a diagnostic code.
```

加上 `--json` 以后（`uri` 本来是完整的 `file:` 路径，这里缩短了）：

```json
{
  "schemaVersion": 1,
  "toolVersion": "sprig-compiler 0.7.1-beta.1",
  "command": "check",
  "exitCode": 1,
  "environment": {"classpath": []},
  "diagnostics": [
    {
      "code": "SPR-MATCH-NONEXHAUSTIVE",
      "phase": "FLOW",
      "severity": "error",
      "uri": "file:///.../main.spr",
      "range": {
        "start": {"line": 5, "character": 11},
        "end": {"line": 7, "character": 51}
      },
      "message": "Missing case: Shape.Square",
      "hint": "Add 'case Shape.Square:' (there is no default case)",
      "relatedHelp": "match",
      "repair": {
        "kind": "add-explicit-case-for-every-missing-case",
        "machineApplicable": false
      },
      "related": [],
      "suggestedEdits": []
    }
  ]
}
```

注意 JSON 里的行号和列号从 0 开始，所以 `"line": 5, "character": 11` 就是普通输出里的第 6 行第 12 列。`relatedHelp` 告诉你该看 `sprig help` 的哪个主题。提示归结为一处机械改写时——比如缺一行 `import "@std/files.spr" as files`，或者函数头要加 `throws Error`——`suggestedEdits` 会直接给出范围和替换文本，照着应用即可。在编辑器里，[语言服务器](#语言服务器)会把同样的改写做成快速修复。

退出码的规则：

- 命令行参数写错，或者工具本身出错，返回 `2`。
- 源码错误和运行时错误，一般返回 `1`。
- `run` 会把程序自己的退出码原样传出来，所以程序主动退出时，也可能返回 `2` 或别的值。从 v0.6.0-beta.1 开始，程序用 `@std/process` 的 `process.exit` 指定退出码；同一个模块还有写标准错误的 `print_error`，以及读标准输入的 `read_line`、`read_lines`、`read_all`。程序以非零状态退出、又没有抛出 JVM 异常时，Sprig 会报 `SPR-PROGRAM-EXIT`，程序的退出码记在 JSON 的 `data.programExitCode` 里。

## 给 AI 助手用

- 运行之前先跑 `sprig check --json`。它只做解析、名字和类型检查，不生成也不执行代码，是最快、最可靠的一道检查。
- 每个错误都带着固定的错误码、所属阶段（`LEX`、`SYNTAX`、`NAME`、`TYPE`、`FLOW`、`JVM`、`RUNTIME`）和准确的位置，很多还带有 `hint`，说明下一步该怎么改。
- `run --json` 把程序输出和错误分开，程序运行失败时也能拿到可以解析的结果。

完整的工作方式见[和 AI 助手一起写代码](/guide/agent-workflow)。

## 格式化

`sprig fmt 文件.spr` 会直接格式化文件；`sprig fmt --check . --json` 只检查、不修改，适合放进 CI。格式化会保留注释，结果是确定的，没有配置项，也不会激进地折行。除了 `fmt`，其他命令都不会改动你的源码。详见[格式化（英文）](/en/reference/tooling/formatter)。

## 语言服务器

`sprig lsp` 通过标准输入输出说 Language Server Protocol（LSP），Neovim、Helix 这类编辑器可以直接用它，[VS Code 插件](/guide/editor)也会自动启动它。它是 v0.6.0-beta.1 新加的。

它提供边写边报错、悬停提示、跳转到定义、查找引用、大纲、补全、格式化、局部变量和参数的重命名，以及快速修复：错误的改法是一处机械改写时，在编辑器里点一下就能改好。这些都来自和 `sprig check` 同一个编译器，所以编辑器里看到的和命令行永远一致。代码还解析不了的时候，服务器宁可什么都不返回，也不去猜。快速修复比 v0.7.1-beta.1 新，要等下一个版本。

编辑器怎么配置、每项功能的细节，见[语言服务器参考（英文）](/en/reference/tooling/lsp)。

## 还没有的

下面这些都还在计划中，目前没有实现：

- 包的发布和模块仓库
- 增量检查

早期的设计提案见 [Agent 工具协议（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/history/design-kit/AGENT_TOOL_PROTOCOL.md)。它只是历史提案，不代表现状。`sprig api` 能查到什么、查不到什么，见 [JVM 互操作参考（英文）](/en/reference/jvm/interop)。
