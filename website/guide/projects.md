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
sprig build --bundle         # 再打一个自带 Java 运行时的目录，给没装 JDK 的机器
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

然后用 `sprig run --bin server` 运行它，`sprig check --bin server` 和 `sprig build --bin server` 也一样只处理这一个 bin。如果声明了多个 bin，又没有指定项目的 `entry`：`sprig check` 不带参数时会检查所有 bin；`build` 和 `run` 只能处理一个程序，必须加 `--bin`。直接给出项目里的文件（比如 `sprig check src/server.spr`）也可以，它照样用项目的依赖来编译。

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

### 从注册表添加

不写 `--path`、`--git`、`--jvm`，`sprig add` 就去包注册表里查这个名字：

```bash
sprig search json            # 注册表里有哪些包，最新版本是什么
sprig add json-codec         # 查到后写成普通的 Git 依赖（git、tag、subdir），锁文件照常锁定提交
sprig add json-codec --version 0.7.1-beta.1
```

注册表只是一份索引：一个目录，里面每个包一个 `packages/名字.toml`，写着它的 Git 仓库、子目录和各个发布版本对应的 tag。它不是新的下载方式，`add` 之后清单里就是完整的 Git 依赖，`check`、`run` 不再碰注册表。项目用 `[[registry]]` 声明自己用哪些注册表：每个表都要有 `name`，再加本地 `path` 或 Git `url`，`--registry` 用的就是这个名字；一个都不写时，默认用 Sprig 仓库里的 `registry/` 目录，里面列着第一方库。

每个版本是一个 SemVer 版本号加一个 Git ref，通常是 tag 以及发布时它指向的提交（`rev`）。最新版本按 SemVer 顺序选，不看列表位置。被撤回（`yanked`）的版本不会再被新依赖选中，但已经锁定它的项目照样能解析；tag 被移动过的版本会被拒绝，因为已发布的版本是不可变的。

### 发布一个包

默认注册表就是 Sprig 仓库里的 `registry/` 目录，发布一个包就是向 `ColinHouse/Sprig` 开一个只改 `registry/packages/名字.toml` 的 pull request，由 CI 的 Registry 工作流验证：

```bash
cd my-package
sprig publish --registry ../Sprig/registry --tag v1.0.0 --license Apache-2.0 --owner your-github-handle
```

`publish` 在本地的索引目录里写好条目，并记下 tag 指向的提交；之后提交这个文件、开 pull request。默认注册表是严格的：包名只能是小写字母、数字和连字符；每个版本是一个固定到提交的 tag（不接受 branch）；必须写 `license`（SPDX 标识）和 `owners`（GitHub 用户名）；已发布的版本不能改也不能删，有问题就撤回：`sprig publish --registry ../Sprig/registry --yank 1.0.0 --reason "原因"`。改别人的条目需要该条目的 owner 发起，或者由维护者批准。工作流会把每个新版本在它的 tag 处克隆下来，核对提交，再用当前 SDK 跑 `sprig resolve`、`sprig check` 和 `sprig test`。

查不到的包或版本会报 `SPR-DEP-REGISTRY`，`sprig search` 能看到到底有什么。细节见[依赖契约（英文）](/en/reference/projects/dependencies)。

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
