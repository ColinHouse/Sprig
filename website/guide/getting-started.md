# 安装 SDK 并创建第一个项目

第一次使用 Sprig？可先从[中英双语入门教程](/tutorial)完成一个本地 Task Tracker，再回到本页查阅安装、项目与发行细节。

Sprig 是面向 CLI 工具、自动化和可靠应用代码的实验性 JVM 语言。
安装 **JDK 17+**，确保 `java` 和 `javac` 都在 `PATH`。SDK 不包含 JDK。

## 安装与升级

Linux/macOS 可使用托管安装器。它会下载官方 SDK ZIP 和 SHA-256 文件，校验后安装到版本目录，并将 `sprig` 放到 `~/.local/bin`：

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig upgrade --check
sprig upgrade
```

如需手工安装，可从 [v0.5.0-beta.1 发行页](https://github.com/ColinHouse/Sprig/releases/tag/v0.5.0-beta.1)下载 ZIP 与 `.sha256`，校验通过后解压并将 `bin` 加入 `PATH`。安装器适用于 Linux/macOS；Windows 仍为实验性预览。详细契约见[安装与升级](/en/reference/projects/install)。

当前已发布实验性 Beta 为 v0.5.0-beta.1（编译器 `0.5.0-beta.1`、语言版本 `0.8-dev`）。它包含 `sprig test`、`sprig wrap`、`run --stacktrace`、schema-4 锁及发行说明列出的 JVM 和一方库能力。请以发行资产和 `sprig capabilities --json` 为准，不要把仓库后续源码能力当成已发布功能。

## 初始化、解析、运行

保存启动器绝对路径，进入新项目后仍可调用：

```bash
./bin/sprig version
SPRIG="$(pwd)/bin/sprig"
"$SPRIG" init my-tool
cd my-tool
"$SPRIG" resolve
"$SPRIG" run
```

程序输出：`Hello, Sprig!`。`init` 创建 `sprig.toml` 和 `src/main.spr`，不覆盖
已有文件；`resolve` 生成 `sprig.lock`；`run` 类型检查、生成 Java、调用 `javac`
并在 JVM 运行。修改源码不用重新生成锁；修改 manifest 或依赖需要重新 resolve。

## Windows 与源码构建

Windows 为实验性预览，不是当前支持的发行平台；可用原生源码构建进行测试；需要 Git、JDK 17+、Python 3.12+，
不需要 Bash 或 Maven CLI。PowerShell：

```powershell
git clone https://github.com/ColinHouse/Sprig.git
Set-Location Sprig
py -3 scripts/build.py
$Sprig = (Resolve-Path .\bin\sprig.cmd).Path
& $Sprig version
& $Sprig init my-tool
Set-Location my-tool
& $Sprig resolve
& $Sprig run
```

Linux/macOS 克隆同一仓库，用 `python3 scripts/build.py`，再执行上面的
`bin/sprig` 流程。首次构建下载固定版本 ANTLR 和 Maven Resolver 库；仅构建
文档及完整贡献验证时需要 Node.js 20+/npm。实际验证过的平台看[发行状态](/project/release-status)。

## 查询、修改、检查、修复

在项目里执行（没有设置 PATH 时用启动器绝对路径代替 `sprig`）：

```bash
sprig capabilities --json
sprig help generics --json
sprig api java.time.LocalDate --json
sprig check --json
sprig test --json          # 运行项目 tests/**/*.spr
sprig explain SPR-TYPE-NULLABLE --json
sprig run
```

诊断提供稳定码与源位置。Java 引用结果须判空，整数除法和泛型参数须显式。
JSON 格式见[工具与 JSON](/guide/tooling)。不要从其他语言猜测规则。

`test`、`wrap` 和 `run --stacktrace` 已包含在 Beta SDK 中。要接入第三方 JVM 库或框架构建，先读
[JVM 互操作](/guide/jvm-interop) 与 [Fabric / JVM 框架集成](/guide/fabric)。

## 做一个实用工具

[Showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)包括
输出 JSON 的仓库审计器、真实 Maven 库应用和源码分析器，各 README 给出输入和命令。
基础语法见可执行的[教程与示例页](/examples)或[语言导览](/guide/language-tour)；应用程序项目见
[示例总览](https://github.com/ColinHouse/Sprig/blob/main/examples/README.md)。

需要把 host 构建系统（Gradle/Loom/Maven）解析出的真实 classpath 交给 Sprig、再把
生成的 Java 交还 host 编译时，见 [Fabric / JVM 框架集成](/guide/fabric) 的可复用
接线与 clean-build/打包清单。

想和编码 Agent 一起贡献？读[贡献指南（英文）](/en/project/contributing)和
`AGENTS.md`，挑选有验收条件的小任务，运行 `scripts/verify.sh`
（Windows：`py -3 scripts/verify.py`），审查后提交 PR。

## 常见问题

- **找不到 JDK**：检查 `java -version` 和 `javac -version`，确认安装的是 JDK。
- **锁缺失或过期**：在项目目录执行 resolve；manifest 修改后需重新解析。
- **离线缓存缺失**：先联网 resolve，锁文件本身不包含所有依赖文件。
- **`SPR-LEX-TAB`**：缩进使用空格。
- **Java 结果可空**：先 `!= null` 收窄再使用。
- **校验不符**：保留证据并重新下载对应文件，不要关闭校验。
