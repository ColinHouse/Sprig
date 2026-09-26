# 五分钟创建第一个项目

Sprig 是面向 CLI 工具、自动化和可靠应用代码的实验性 JVM 语言。
安装 **JDK 17+**，确保 `java` 和 `javac` 都在 `PATH`。SDK 不包含 JDK。

## 下载与校验

公开 SDK 为 [v0.2.0-alpha.1](https://github.com/ColinHouse/Sprig/releases/tag/v0.2.0-alpha.1)。
从发行页下载 ZIP 与同名 `.sha256`。Linux/macOS：

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.2.0-alpha.1/sprig-v0.2.0-alpha.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.2.0-alpha.1/sprig-v0.2.0-alpha.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.2.0-alpha.1-jdk.zip.sha256
unzip sprig-v0.2.0-alpha.1-jdk.zip
cd sprig-v0.2.0-alpha.1-jdk
```

Linux 也可用 `sha256sum -c`；校验不符时停止。源代码里程碑目标是
v0.3.0-alpha.1；已发布 SDK 与源码功能请分别看发行资产和 capability 输出。
v0.2 发行包支持本地/Git 依赖，第三方 JVM 库需显式 classpath。

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

Windows SDK 发布前可用原生源码构建；需要 Git、JDK 17+、Python 3.12+，
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
sprig explain SPR-TYPE-NULLABLE --json
sprig run
```

诊断提供稳定码与源位置。Java 引用结果须判空，整数除法和泛型参数须显式。
JSON 格式见[工具与 JSON](/guide/tooling)。不要从其他语言猜测规则。

## 做一个实用工具

[Showcases](https://github.com/ColinHouse/Sprig/tree/main/examples/showcases)包括
输出 JSON 的仓库审计器、真实 Maven 库应用和源码分析器，各 README 给出输入和命令。
基础语法见[语言导览](/guide/language-tour)。

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
