# 快速开始：安装 Sprig

这一页讲怎么把 Sprig 装好，并跑起第一个项目。装好以后，接着看[入门教程](/tutorial/)。

## 先装 JDK

Sprig 会把代码编译成 Java 再交给 JVM 运行，所以需要 **JDK 21 或更新的版本**。注意是 JDK，不是只有 JRE。检查一下：

```bash
java -version
javac -version
```

两条命令都有输出，就可以继续了。SDK 本身不带 JDK。

## 安装 SDK（Linux / macOS）

最省事的办法是用安装脚本。它会下载官方发布的 SDK，核对 SHA-256 校验值，再把 `sprig` 命令放进 `~/.local/bin`：

```bash
curl -fsSL https://raw.githubusercontent.com/ColinHouse/Sprig/main/scripts/install-sprig.sh | sh
export PATH="$HOME/.local/bin:$PATH"
sprig version
```

以后想升级，运行 `sprig upgrade --check` 看看有没有新版本，再用 `sprig upgrade` 升级。

### 手动下载

也可以从[发布页](https://github.com/ColinHouse/Sprig/releases/tag/v0.7.1-beta.1)下载 ZIP，自己校验后解压：

```bash
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip
curl -LO https://github.com/ColinHouse/Sprig/releases/download/v0.7.1-beta.1/sprig-v0.7.1-beta.1-jdk.zip.sha256
shasum -a 256 -c sprig-v0.7.1-beta.1-jdk.zip.sha256
unzip sprig-v0.7.1-beta.1-jdk.zip
```

Linux 上也可以用 `sha256sum -c`。如果校验没通过，请重新下载，不要跳过校验。解压后把目录里的 `bin` 加进 `PATH`。

## 第一个项目

```bash
sprig init my-tool
cd my-tool
sprig resolve
sprig run
```

看到 `Hello, Sprig!` 就成功了。这几条命令分别做了这些事：

- `sprig init` 创建 `sprig.toml`（项目配置）和 `src/main.spr`（程序入口），已经存在的文件不会被覆盖。
- `sprig resolve` 解析依赖，生成锁文件 `sprig.lock`。只改代码不用重新运行它；改了 `sprig.toml` 或依赖以后才需要。
- `sprig run` 检查代码、生成 Java、编译并运行。

## 常用命令

| 命令 | 用途 |
|---|---|
| `sprig check` | 只检查，不运行，一次列出所有错误 |
| `sprig run` | 检查并运行 |
| `sprig test` | 运行 `tests/` 下的测试 |
| `sprig fmt .` | 把代码格式化成统一风格 |
| `sprig explain <错误码>` | 解释一个错误码：原因、修法、正反例 |
| `sprig help <主题>` | 语法速查，比如 `sprig help nullability` |
| `sprig api <Java 类>` | 查一个 Java 类的方法在 Sprig 里的签名 |

这些命令几乎都能加 `--json`，输出结构化的结果，方便编辑器、脚本或 AI 编程助手读取。详见[工具与 JSON](/guide/tooling)。

编辑器方面，有一个 [VS Code 插件](/guide/editor)（目前是本地预览版），支持语法高亮、保存时检查和一键运行。

## Windows 和从源码构建

Windows 目前还是实验性支持，安装脚本只适用于 Linux 和 macOS。发布的 ZIP 里带有 Windows 启动器 `bin\sprig.cmd`：先核对 ZIP 的 SHA-256，解压后运行这个启动器即可（见 [Windows 安装说明（英文）](/en/reference/projects/install#windows)）。也可以从源码构建，需要 Git、JDK 17+ 和 Python 3.12+，不需要 Bash 或 Maven。在 PowerShell 里：

```powershell
git clone https://github.com/ColinHouse/Sprig.git
Set-Location Sprig
py -3 scripts/build.py
$Sprig = (Resolve-Path .\bin\sprig.cmd).Path
& $Sprig init my-tool
Set-Location my-tool
& $Sprig resolve
& $Sprig run
```

在 Linux 或 macOS 上从源码构建也一样：克隆仓库，运行 `python3 scripts/build.py`，然后使用仓库里的 `bin/sprig`。第一次构建会下载固定版本的 ANTLR 和 Maven Resolver。

## 遇到问题

- **找不到 `javac`**：装的可能是 JRE，换成 JDK。
- **提示锁文件缺失或过期**：在项目目录运行 `sprig resolve`。
- **离线时依赖缓存不完整**：先联网运行一次 `sprig resolve`。
- **报错 `SPR-LEX-TAB`**：Sprig 的缩进只能用空格，不能用 Tab。
- **Java 方法的返回值不能直接用**：Sprig 把它当作可能为 `null`，先用 `if x != null:` 判断。
- **校验值对不上**：重新下载对应的文件，不要关掉校验。

还是没解决？欢迎[开一个 issue](https://github.com/ColinHouse/Sprig/issues)，贴上命令和完整输出。
