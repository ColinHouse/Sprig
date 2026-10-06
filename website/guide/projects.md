# 项目

只有一个文件时，`sprig run hello.spr` 就够用了。等代码多起来，或者要用别人的包、Java 库，就该建一个项目了。项目就是一个带 `sprig.toml` 的目录。

## 项目的结构

`sprig init my-tool` 会生成两个文件：

```text
my-tool/
├── sprig.toml        # 项目配置
└── src/
    └── main.spr      # 程序入口
```

之后运行 `sprig resolve` 会多出一个锁文件 `sprig.lock`，`sprig build` 的输出放在 `sprig-build/` 里，测试程序放在 `tests/` 下。

`sprig.toml` 一开始是这样的：

```toml
[project]
name = "my-tool"
version = "0.1.0"
language = "0.8"
```

- 源码目录默认是 `src`，入口默认是 `src/main.spr`，可以在 `[project]` 里用 `source` 和 `entry` 修改。
- 包名由 `sprig.toml` 决定，`.spr` 文件里不用写 `package`。
- 字段名写错、字段重复、值的类型不对，都会被拒绝。

## 常用命令

在项目的任何子目录里都能运行这些命令，Sprig 会往上找到 `sprig.toml`：

```bash
sprig check                  # 检查入口，以及它导入的所有文件
sprig run                    # 检查并运行
sprig build                  # 生成 Java 和 class 文件
sprig test                   # 运行 tests/ 下的测试
sprig project                # 查看项目信息
sprig run path/to/file.spr   # 指定了文件，就以你指定的为准
```

`sprig project --json` 会给出项目的根目录、名称、版本、入口、锁文件状态和依赖列表，脚本和 AI 助手不用自己去解析 TOML。

`tests/` 下的每个 `.spr` 文件都是一个普通程序，`sprig test` 会把它们分别放在独立的 JVM 里运行。`tests/compile_fail/` 下的文件则应该编译失败，同名的 `.expect.toml` 里写着预期的错误码。详见[项目测试（英文）](/en/reference/tooling/testing)。

## 多个入口

一个项目可以有好几个能运行的入口，用 `[[bin]]` 声明：

```toml
[[bin]]
name = "server"
entry = "src/server.spr"
```

然后用 `sprig run --bin server` 运行它。如果声明了多个 bin，又没有指定项目的 `entry`，运行时就必须加 `--bin`。

## 添加依赖

最省事的办法是 `sprig add`。它会修改 `sprig.toml`，并立刻更新锁文件：

```bash
sprig add math --path ../math                                     # 本地目录里的 Sprig 包
sprig add math --git https://example.com/math.git --branch main   # Git 仓库里的 Sprig 包
sprig add --jvm org.apache.commons:commons-text:1.12.0            # Maven 上的 Java 库
sprig remove math
```

`add` 和 `remove` 只改动对应的依赖块，`sprig.toml` 里的其他内容保持原样。

你也可以手动编辑 `sprig.toml`，改完以后运行一次 `sprig resolve`。三种依赖分别是这样写的：

```toml
# 本地目录
[[dependency]]
name = "math"
path = "../math"
```

```toml
# Git 仓库：branch、tag、rev 三选一；subdir 可选，表示包在仓库里的子目录
[[dependency]]
name = "math"
git = "https://example.com/math.git"
branch = "main"
```

```toml
# Maven：只接受精确的版本号
[[jvm]]
group = "org.apache.commons"
artifact = "commons-text"
version = "1.12.0"
```

`name` 是你在自己代码里导入这个包时用的名字，和依赖包自己 `[project]` 里的 `name` 无关。不同的包可以给依赖起同样的名字，同一个包里不能重名。

## 使用依赖里的模块

依赖包要在自己的 `sprig.toml` 里写明，哪些模块允许别人导入：

```toml
[project]
name = "math"
version = "0.1.0"
language = "0.8"
exports = ["vector.spr"]
```

然后在你的项目里，用 `@` 加依赖名来导入：

```sprig
import "@math/vector.spr" as vector

print(vector.length_squared(3, 4))
```

只有 `exports` 里列出的模块可以导入。路径会先被规范化，没法用 `..` 跳出依赖包的源码目录。

## 锁文件

`sprig resolve`（以及 `add`、`remove`）会生成 `sprig.lock`，里面记下每个依赖解析到的确切版本和校验值。

- **把它提交到版本库。** 这样别人构建出来的结果和你的完全一样。
- **只有三个命令会改它。** `check`、`build`、`run` 只读锁文件。锁文件不存在，或者和 `sprig.toml` 对不上时，它们会直接报错，不会自己去解析依赖。能更新锁文件的只有 `resolve`、`add` 和 `remove`。
- **Git 依赖锁定到具体的 commit。** 之后分支怎么移动，已经锁定的构建都不受影响。想更新，就重新运行 `sprig resolve`。
- **换了 SDK 要重新解析。** 锁文件记录了编译器版本，换成别的版本后，`check` 会报 `SPR-PROJECT-LOCK-STALE`，运行一次 `sprig resolve` 就好。
- **锁文件格式目前是第 5 版。** 用相对路径声明的本地依赖记成相对位置（`portable = true`），整个工作区可以一起搬走；用绝对路径声明的记为 `portable = false`。内置的 `@std` 标准库来自你安装的 SDK，不写进锁文件。
- **v0.5.0-beta.1 用的是第 4 版。** 那一版还会把 `@std` 的版本记进锁文件。升级以后，运行一次 `sprig resolve`，旧的锁文件就会按第 5 版重写。

## 离线使用

- 加上 `--offline`，Sprig 只用本地缓存。Git 缓存在 `~/.sprig/git`，缓存里没有需要的版本时会报 `SPR-DEP-OFFLINE`。
- 第一次解析 Maven 依赖需要联网。之后只要缓存是完整的，离线也能构建。
- 每次使用 Git 缓存前都会校验它（HEAD、标记文件、已跟踪和未跟踪的文件），被改动过就会报错。
- 符号链接按真实路径检查，不能借它跳出依赖目录。指向依赖内部、并且被导出的符号链接是允许的。

## Maven 依赖的更多细节

解析 Maven 依赖用的是 Apache Maven Resolver，父 POM、BOM 和传递依赖都会处理。锁文件会记录每个 JAR 和 POM 的 SHA-256、依赖关系和 classpath 顺序。解析完以后，`check`、`build`、`run`、`api` 和 `doctor` 会自动用上这些 JAR，不用你手动去找文件，它们也不会重新解析依赖。

依赖之间出现环，或者别名重复，都会被拒绝，并给出结构化的错误。完整规则见[依赖说明（英文）](https://github.com/ColinHouse/Sprig/blob/main/docs/projects/dependencies.md)。

## 不建项目也可以

不在任何项目里的 `.spr` 文件可以直接运行，不需要 `sprig.toml`。但如果这个文件位于某个项目的源码目录里，Sprig 就会把它当成项目的一部分：用这个项目的依赖，并且要求锁文件是最新的。
